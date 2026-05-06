package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.OwnerMapper;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.OwnerRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.External.PropertyServiceFeig;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OwnerServiceImpul implements OwnerService {

    private final OwnerRepo ownerRepo;
    private final UserRepo userRepo;
    private final UserServiceEvents userServiceEvent;
    private final PropertyServiceFeig propertyServiceFeig;

    public OwnerServiceImpul(OwnerRepo ownerRepo,
                             UserRepo userRepo,
                             UserServiceEvents userServiceEvent,
                             PropertyServiceFeig propertyServiceFeig) {
        this.ownerRepo = ownerRepo;
        this.userRepo = userRepo;
        this.userServiceEvent = userServiceEvent;
        this.propertyServiceFeig = propertyServiceFeig;
    }

    @Override
    @Transactional
    public OwnerResponseDto createOwner(OwnerRequestDto ownerRequest) {
        // Sanity-check the linked user exists before creating an owner record
        userRepo.findActiveById(ownerRequest.userId()).orElseThrow(
                () -> new RecordNotFound("Cannot create owner: linked user not found id=" + ownerRequest.userId()));

        Owners owner = OwnerMapper.toEntity(ownerRequest);
        LocalDateTime now = LocalDateTime.now();
        owner.setCreatedAt(now);
        owner.setUpdatedAt(now);
        Owners savedOwner = ownerRepo.save(owner);

        userServiceEvent.sendOwnerRegistered(OwnerRegisteredEvent.builder()
                .eventType("owner.registered")
                .ownerId(savedOwner.getId())
                .timestamp(Instant.now())
                .build());

        log.info("Owner created id={} businessName={}", savedOwner.getId(), savedOwner.getBusinessName());
        return OwnerMapper.toDto(savedOwner);
    }

    @Override
    public OwnerResponseDto getOwnerById(String ownerId) {
        Owners owner = ownerRepo.findById(ownerId).orElseThrow(
                () -> new RecordNotFound("Owner not found with id: " + ownerId));
        return OwnerMapper.toDto(owner);
    }

    @Override
    @Transactional
    public OwnerResponseDto updateOwner(String ownerId, OwnerRequestDto ownerRequest) {
        Owners existing = ownerRepo.findById(ownerId).orElseThrow(
                () -> new RecordNotFound("Owner not found with id: " + ownerId));

        if (notBlank(ownerRequest.userId()))           existing.setUserId(ownerRequest.userId());
        if (notBlank(ownerRequest.businessName()))     existing.setBusinessName(ownerRequest.businessName());
        if (notBlank(ownerRequest.gstNumber()))        existing.setGstNumber(ownerRequest.gstNumber());
        if (notBlank(ownerRequest.panNumber()))        existing.setPanNumber(ownerRequest.panNumber());
        if (notBlank(ownerRequest.bankAccountNumber())) existing.setBankAccountNumber(ownerRequest.bankAccountNumber());
        if (notBlank(ownerRequest.ifscCode()))         existing.setIfscCode(ownerRequest.ifscCode());
        if (ownerRequest.totalProperties() != null)    existing.setTotalProperties(ownerRequest.totalProperties());
        existing.setUpdatedAt(LocalDateTime.now());
        return OwnerMapper.toDto(ownerRepo.save(existing));
    }

    @Override
    public Page<OwnerResponseDto> getAllOwners(Pageable pageable) {
        return ownerRepo.findAll(pageable).map(OwnerMapper::toDto);
    }

    /**
     * Resolves an owner's tenants by:
     * <ol>
     *   <li>Calling Property Service via Feign to get tenant IDs across the
     *       owner's buildings (cross-service lookup — flat→tenant linkage is
     *       owned by Property Service, not User Service).</li>
     *   <li>Hydrating each tenant id from {@link UserRepo} locally.</li>
     * </ol>
     * Returns an empty list (not 404) if the owner has no occupied flats —
     * "no tenants yet" is a valid business state, not an error.
     */
    @Override
    public List<UserResponseDto> getTenentsByOwnerId(String ownerId) {
        // Validate the owner exists first so callers get a clear 404 rather
        // than a misleading empty list.
        ownerRepo.findById(ownerId).orElseThrow(
                () -> new RecordNotFound("Owner not found with id: " + ownerId));

        List<String> tenantIds;
        try {
            tenantIds = propertyServiceFeig.getTenantIdsByOwner(ownerId);
        } catch (Exception ex) {
            log.warn("Property Service unavailable when resolving tenants for owner {}: {}", ownerId, ex.toString());
            return List.of();
        }
        if (tenantIds == null || tenantIds.isEmpty()) return List.of();

        List<UserResponseDto> tenants = new ArrayList<>(tenantIds.size());
        for (String tid : tenantIds) {
            userRepo.findActiveById(tid).map(UserMapper::toDto).ifPresent(tenants::add);
        }
        return tenants;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
