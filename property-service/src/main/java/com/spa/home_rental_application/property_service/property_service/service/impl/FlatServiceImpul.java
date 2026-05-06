package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.FlatMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.FlatOccupiedException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.InvalidLeasePeriodException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FlatServiceImpul implements FlatService {

    private final FlatRepo flatRepo;
    private final BuildingRepo buildingRepo;
    private final PropertyServiceEvents eventProducer;
    private final FlatMapper flatMapper;
    private final AgreementService agreementService;

    public FlatServiceImpul(FlatRepo flatRepo,
                            BuildingRepo buildingRepo,
                            PropertyServiceEvents eventProducer,
                            FlatMapper flatMapper,
                            AgreementService agreementService) {
        this.flatRepo = flatRepo;
        this.buildingRepo = buildingRepo;
        this.eventProducer = eventProducer;
        this.flatMapper = flatMapper;
        this.agreementService = agreementService;
    }

    @Override
    public Page<FlatResponseDTO> getAllFlats(Pageable pageable) {
        Page<Flat> flats = flatRepo.getActiveFlats(pageable);
        List<FlatResponseDTO> mapped = flatMapper.toResponseList(flats.getContent());
        return new PageImpl<>(mapped, pageable, flats.getTotalElements());
    }

    @Override
    public FlatResponseDTO getflatById(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));
        return flatMapper.toResponseDTO(flat);
    }

    @Override
    @Transactional
    public FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO) {
        Flat flat = flatMapper.toEntity(flatRequestDTO);
        if (flat.getId() == null || flat.getId().isBlank()) {
            flat.setId("FLT-" + UUID.randomUUID());
        }

        Building parent = (flat.getBuildingId() == null) ? null
                : buildingRepo.findById(flat.getBuildingId()).orElseThrow(
                    () -> new RecordNotFoundException(
                            "Building not found for buildingId=" + flat.getBuildingId()));

        LocalDateTime now = LocalDateTime.now();
        if (flat.getCreatedAt() == null) flat.setCreatedAt(now);
        flat.setUpdatedAt(now);
        Flat saved = flatRepo.save(flat);

        syncBuildingFlatCount(parent);
        return flatMapper.toResponseDTO(saved, parent);
    }

    @Override
    @Transactional
    public FlatResponseDTO deleteFlatById(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));
        flat.setIsDeleted(true);
        flat.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(flat);

        Building parent = (flat.getBuildingId() == null) ? null
                : buildingRepo.findById(flat.getBuildingId()).orElse(null);
        syncBuildingFlatCount(parent);

        return flatMapper.toResponseDTO(saved, parent);
    }

    @Override
    public List<FlatResponseDTO> getflatsByBuildingId(String buildId) {
        buildingRepo.findById(buildId).orElseThrow(
                () -> new RecordNotFoundException("Building not found with id: " + buildId));
        return flatMapper.toResponseList(flatRepo.findByBuildingId(buildId));
    }

    @Override
    public List<FlatResponseDTO> getAllVacentFlats() {
        return flatMapper.toResponseList(flatRepo.findByIsOccupiedFalse());
    }

    @Override
    public List<FlatResponseDTO> getflatsByTenantId(String tenantId) {
        return flatMapper.toResponseList(flatRepo.findActiveByTenantId(tenantId));
    }

    @Override
    @Transactional
    public FlatResponseDTO makeFlatVacate(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        if (Boolean.FALSE.equals(flat.getIsOccupied())) {
            return flatMapper.toResponseDTO(flat);
        }

        if (flat.getLeaseStartDate() != null) {
            LocalDate earliestVacate = flat.getLeaseStartDate().plusMonths(2);
            if (LocalDate.now().isBefore(earliestVacate)) {
                throw new InvalidLeasePeriodException(
                        "Cannot vacate flat " + flat.getFlatNumber()
                        + " before " + earliestVacate
                        + " -- a minimum 2-month occupancy is required.");
            }
        }

        String tenantBeingVacated = flat.getTenantId();
        String leaseEnd = flat.getLeaseEndDate() != null ? flat.getLeaseEndDate().toString() : null;

        flatRepo.markFlatVacant(flatId);
        flat.setIsOccupied(false);
        flat.setTenantId(null);
        flat.setUpdatedAt(LocalDateTime.now());

        eventProducer.sendFlatVacated(FlatVacatedEvent.builder()
                .eventType("flat.vacated")
                .flatId(flatId)
                .tenantId(tenantBeingVacated)
                .endDate(leaseEnd)
                .timestamp(Instant.now())
                .build());

        return flatMapper.toResponseDTO(flat);
    }

    @Override
    @Transactional
    public FlatResponseDTO updateFlat(String flatId, FlatRequestDTO flatRequestDTO) {
        Flat dto = flatMapper.toEntity(flatRequestDTO);
        Flat existing = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        Building oldParent = null;
        if (dto.getBuildingId() != null && !dto.getBuildingId().equals(existing.getBuildingId())) {
            oldParent = buildingRepo.findById(existing.getBuildingId()).orElse(null);
            buildingRepo.findById(dto.getBuildingId()).orElseThrow(
                    () -> new RecordNotFoundException(
                            "Building not found for buildingId=" + dto.getBuildingId()));
        }

        existing.setBuildingId(dto.getBuildingId());
        existing.setFlatNumber(dto.getFlatNumber());
        existing.setFloor(dto.getFloor());
        existing.setBedrooms(dto.getBedrooms());
        existing.setBathrooms(dto.getBathrooms());
        existing.setAreaSqft(dto.getAreaSqft());
        existing.setRentAmount(dto.getRentAmount());
        existing.setLeaseStartDate(dto.getLeaseStartDate());
        existing.setLeaseEndDate(dto.getLeaseEndDate());
        existing.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(existing);

        if (oldParent != null) syncBuildingFlatCount(oldParent);
        Building newParent = (saved.getBuildingId() == null) ? null
                : buildingRepo.findById(saved.getBuildingId()).orElse(null);
        if (newParent != null) syncBuildingFlatCount(newParent);

        return flatMapper.toResponseDTO(saved, newParent);
    }

    @Override
    @Transactional
    public FlatResponseDTO assignFlat(String flatId, AssignFlatRequest req) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        if (Boolean.TRUE.equals(flat.getIsOccupied())) {
            throw new FlatOccupiedException(
                    "Flat " + flatId + " is already occupied by tenant " + flat.getTenantId());
        }
        if (req.leaseEndDate().isBefore(req.leaseStartDate())) {
            throw new InvalidLeasePeriodException("leaseEndDate cannot be before leaseStartDate");
        }

        flat.setTenantId(req.tenantId());
        flat.setLeaseStartDate(req.leaseStartDate());
        flat.setLeaseEndDate(req.leaseEndDate());
        flat.setIsOccupied(true);
        flat.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(flat);

        eventProducer.sendFlatOccupied(FlatOccupiedEvent.builder()
                .eventType("flat.occupied")
                .flatId(saved.getId())
                .tenantId(saved.getTenantId())
                .buildingId(saved.getBuildingId())
                .rentAmount(saved.getRentAmount() != null ? saved.getRentAmount().doubleValue() : null)
                .startDate(saved.getLeaseStartDate() != null ? saved.getLeaseStartDate().toString() : null)
                .timestamp(Instant.now())
                .build());

        // Auto-create a PENDING_SIGNATURE lease agreement so the tenant
        // immediately sees something to review and sign on their dashboard.
        // Failures here don't roll back the assignment -- owner can
        // regenerate via a manual endpoint later if it ever blows up.
        try {
            agreementService.createForAssignment(saved);
        } catch (Exception ex) {
            log.error("Could not auto-create lease agreement for flat {}", saved.getId(), ex);
        }

        return flatMapper.toResponseDTO(saved);
    }

    /**
     * Recompute the building's static buildingTotalFlats field to reflect
     * the live count of non-deleted flats. Keeps the legacy field truthful.
     */
    private void syncBuildingFlatCount(Building parent) {
        if (parent == null) return;
        long active = flatRepo.findByBuildingId(parent.getBuildingId()).stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .count();
        parent.setBuildingTotalFlats(String.valueOf(active));
        parent.setUpdatedDt(LocalDateTime.now().toString());
        buildingRepo.save(parent);
    }
}
