package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.BuildingMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BuildingImpul implements BuildingService {

    private final BuildingRepo building_repo;
    private final FlatRepo flat_repo;
    private final PropertyServiceEvents eventProducer;

    public BuildingImpul(BuildingRepo building_repo,
                         FlatRepo flat_repo,
                         PropertyServiceEvents eventProducer) {
        this.building_repo = building_repo;
        this.flat_repo = flat_repo;
        this.eventProducer = eventProducer;
    }

    @Override
    public Page<BuildingResponseDTO> getBuildings(Pageable pageable) {
        Page<Building> buildings = building_repo.getActiveBuildings(pageable);
        // Live counts -- pass the flat repo so each DTO carries real
        // active/occupied/vacant numbers, not the static "design capacity".
        return buildings.map(b -> BuildingMapper.toDTO(b, flat_repo));
    }

    @Override
    @Transactional
    public BuildingResponseDTO createBuilding(BuildingRequestDTO buildingRequestDTO) {
        log.info("Implimentation of building request.");

        Building building = BuildingMapper.toEntity(buildingRequestDTO);

        if (building.getBuildingId() == null || building.getBuildingId().isBlank()) {
            String bid = String.valueOf(UUID.randomUUID());
            log.info("Building Id is found null, hence setting up the id to ID: {}", bid);
            building.setBuildingId("BLD-" + bid);
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(formatter);

        if (building.getCreatedDt() == null || building.getCreatedDt().isBlank()) {
            building.setCreatedDt(now);
        }
        building.setUpdatedDt(now);

        Building saved = building_repo.save(building);
        eventProducer.sendPropertyCreated(PropertyCreatedEvent.builder()
                .eventType("property.created")
                .propertyId(saved.getBuildingId())
                .ownerId(saved.getOwnerId())
                .timestamp(Instant.now())
                .build());
        return BuildingMapper.toDTO(saved, flat_repo);
    }

    @Override
    public BuildingResponseDTO getBuildingById(String buildId) {
        Building building = building_repo.findById(buildId).orElseThrow(
                () -> new RecordNotFoundException("No Record found with the given id :" + buildId));
        return BuildingMapper.toDTO(building, flat_repo);
    }

    @Override
    @Transactional
    public BuildingResponseDTO deleteBuildingById(String buildId) {
        Building building = building_repo.findById(buildId)
                .orElseThrow(() -> new RecordNotFoundException("No record found with id: " + buildId));

        // Real flat-existence check -- string field on the entity is unreliable
        // because it captures planned capacity, not the actual count of created flats.
        long activeFlats = flat_repo.findByBuildingId(buildId).stream()
                .filter(f -> Boolean.FALSE.equals(f.getIsDeleted()) || f.getIsDeleted() == null)
                .count();

        if (activeFlats > 0) {
            throw new BuildingHasFlatsException(
                    "Building " + buildId + " has " + activeFlats + " active flat(s) and cannot be deleted.");
        }

        building.setIsDeleted(true);
        building.setUpdatedDt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        building_repo.save(building);

        return BuildingMapper.toDTO(building, flat_repo);
    }

    @Override
    @Transactional
    public BuildingResponseDTO updateBuilding(String buildId, BuildingRequestDTO buildingRequestDTO) {
        Building building = BuildingMapper.toEntity(buildingRequestDTO);

        Building matchedBuilding = building_repo.findById(buildId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "No record found with the given id: " + buildId));

        if (building.getBuildingName() != null && !building.getBuildingName().isBlank()) {
            matchedBuilding.setBuildingName(building.getBuildingName());
        }
        if (building.getOwnerId() != null && !building.getOwnerId().isBlank()) {
            matchedBuilding.setOwnerId(building.getOwnerId());
        }
        if (building.getBuildingAddress() != null && !building.getBuildingAddress().isBlank()) {
            matchedBuilding.setBuildingAddress(building.getBuildingAddress());
        }
        if (building.getBuildingCity() != null && !building.getBuildingCity().isBlank()) {
            matchedBuilding.setBuildingCity(building.getBuildingCity());
        }
        if (building.getBuildingState() != null && !building.getBuildingState().isBlank()) {
            matchedBuilding.setBuildingState(building.getBuildingState());
        }
        if (building.getBuildingTotalFloors() != null && !building.getBuildingTotalFloors().isBlank()) {
            matchedBuilding.setBuildingTotalFloors(building.getBuildingTotalFloors());
        }
        if (building.getBuildingTotalFlats() != null && !building.getBuildingTotalFlats().isBlank()) {
            matchedBuilding.setBuildingTotalFlats(building.getBuildingTotalFlats());
        }
        if (building.getAmenities() != null && !building.getAmenities().isBlank()) {
            matchedBuilding.setAmenities(building.getAmenities());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(formatter);
        matchedBuilding.setUpdatedDt(now);

        Building saved = building_repo.save(matchedBuilding);

        eventProducer.sendPropertyUpdated(PropertyUpdatedEvent.builder()
                .eventType("Property-Updated")
                .propertyId(saved.getBuildingId())
                .ownerId(saved.getOwnerId())
                .timestamp(Instant.now())
                .build());
        return BuildingMapper.toDTO(saved, flat_repo);
    }

    @Override
    public List<BuildingResponseDTO> getBuildingsByOwnerId(String ownerId) {
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        // Empty list is a valid state -- a brand new owner who hasn't listed
        // anything yet. The frontend dashboard renders an "empty" card here,
        // not a 404.
        return ownerBuildings.stream()
                .map(b -> BuildingMapper.toDTO(b, flat_repo))
                .collect(Collectors.toList());
    }

    /**
     * Returns the tenantIds of every currently-occupied flat across every
     * building owned by the given owner. Returns an empty list (not 404)
     * when the owner has no occupied flats -- callers expect a list.
     */
    @Override
    public List<String> getTenantIdsByOwner(String ownerId) {
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        if (ownerBuildings.isEmpty()) {
            return List.of();
        }
        return ownerBuildings.stream()
                .flatMap(b -> flat_repo.findByBuildingId(b.getBuildingId()).stream())
                .filter(f -> Boolean.TRUE.equals(f.getIsOccupied()))
                .map(f -> f.getTenantId())
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
