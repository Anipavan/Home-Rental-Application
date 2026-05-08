package com.spa.home_rental_application.compliance_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.ReraRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.ComplianceServiceEvents;
import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateReraLeaseRequest;
import com.spa.home_rental_application.compliance_service.DTO.Request.ReraRegisterRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.ReraRegistrationResponseDto;
import com.spa.home_rental_application.compliance_service.Entities.ReraRegistration;
import com.spa.home_rental_application.compliance_service.Exceptionclass.ReraAlreadyRegisteredException;
import com.spa.home_rental_application.compliance_service.Exceptionclass.ReraNotFoundException;
import com.spa.home_rental_application.compliance_service.mapper.ComplianceMapper;
import com.spa.home_rental_application.compliance_service.provider.ReraPortalAdapter;
import com.spa.home_rental_application.compliance_service.repository.ReraRegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ReraServiceImpl implements ReraService {

    private final ReraRegistrationRepository repository;
    private final ReraPortalAdapter adapter;
    private final ComplianceMapper mapper;
    private final ComplianceServiceEvents events;

    public ReraServiceImpl(ReraRegistrationRepository repository,
                           ReraPortalAdapter adapter,
                           ComplianceMapper mapper,
                           ComplianceServiceEvents events) {
        this.repository = repository;
        this.adapter = adapter;
        this.mapper = mapper;
        this.events = events;
    }

    @Override
    @Transactional
    public ReraRegistrationResponseDto register(ReraRegisterRequest request) {
        log.info("RERA register propertyId={} state={}", request.propertyId(), request.state());

        if (repository.existsByPropertyIdAndState(request.propertyId(), request.state())) {
            throw new ReraAlreadyRegisteredException(
                    "Property " + request.propertyId() + " already RERA-registered in " + request.state());
        }

        ReraPortalAdapter.RegistrationResult result = adapter.register(request);

        ReraRegistration record = ReraRegistration.builder()
                .propertyId(request.propertyId())
                .ownerId(request.ownerId())
                .state(request.state())
                .reraRegistrationNumber(result.reraRegistrationNumber())
                .reraPortalId(result.reraPortalId())
                .registrationStatus(result.success() ? "REGISTERED" : "PENDING")
                .registeredAt(result.success() ? LocalDateTime.now() : null)
                .expiryDate(result.expiryDate())
                .failureReason(result.failureReason())
                .build();

        ReraRegistration saved = repository.save(record);

        if (Boolean.TRUE.equals(result.success())) {
            events.sendReraRegistered(ReraRegisteredEvent.builder()
                    .eventType("rera.registered")
                    .propertyId(saved.getPropertyId())
                    .ownerId(saved.getOwnerId())
                    .state(saved.getState())
                    .reraRegistrationNumber(saved.getReraRegistrationNumber())
                    .reraPortalId(saved.getReraPortalId())
                    .registrationStatus(saved.getRegistrationStatus())
                    .registeredAt(saved.getRegisteredAt())
                    .expiryDate(saved.getExpiryDate())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        return mapper.toReraResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReraRegistrationResponseDto> getStatusForProperty(String propertyId) {
        List<ReraRegistration> records = repository.findByPropertyId(propertyId);
        if (records.isEmpty()) {
            throw new ReraNotFoundException("No RERA registration for propertyId=" + propertyId);
        }
        return records.stream().map(mapper::toReraResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String generateReraLeaseMetadata(GenerateReraLeaseRequest request) {
        ReraRegistration r = repository.findByPropertyIdAndState(request.propertyId(), request.state())
                .orElseThrow(() -> new ReraNotFoundException(
                        "Property " + request.propertyId()
                                + " not RERA-registered in " + request.state()));
        if (!"REGISTERED".equals(r.getRegistrationStatus())) {
            throw new ReraNotFoundException(
                    "RERA registration for property " + request.propertyId()
                            + " is in status " + r.getRegistrationStatus());
        }
        return "RERA " + request.state() + " Reg# " + r.getReraRegistrationNumber()
                + " (valid till " + r.getExpiryDate() + ")";
    }
}
