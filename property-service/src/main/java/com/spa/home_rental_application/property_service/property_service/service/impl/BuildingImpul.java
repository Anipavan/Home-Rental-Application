package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.PropertyEvent;
import com.spa.home_rental_application.property_service.property_service.DTO.BuildingMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
    private final PropertyEvent eventProducer;

    public BuildingImpul(BuildingRepo building_repo, PropertyEvent eventProducer) {
        this.building_repo = building_repo;
        this.eventProducer = eventProducer;
    }
    @Override
    public Page<BuildingResponseDTO> getBuildings(Pageable pageable) {
        Page<Building> buildings=building_repo.findAll(pageable);

        return  buildings.map(BuildingMapper::toDTO);
    }

    @Override
    public BuildingResponseDTO createBuilding(BuildingRequestDTO buildingRequestDTO) {
        log.info("Implimentation of building request.");

        Building building= BuildingMapper.toEntity(buildingRequestDTO);

        if (building.getBuildingId() == null || building.getBuildingId().isBlank()) {
            String bid= String.valueOf(UUID.randomUUID());
            log.info("Building Id is found null, hence setting up the id to ID: {}",bid);
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
        return BuildingMapper.toDTO(saved);
    }
    @Override
    public BuildingResponseDTO getBuildingById(String buildId)
    {
        Building building=building_repo.findById(buildId).orElseThrow(
                ()-> new RecordNotFoundException("No Record found with the given id :"+buildId));
        return BuildingMapper.toDTO(building);
    }

    @Override
    public BuildingResponseDTO deleteBuildingById(String buildId)
    {
        Building building=building_repo.findById(buildId).orElseThrow(() -> new RecordNotFoundException("No record found with the given id: " + buildId));
        if(building.getBuildingTotalFlats().isBlank()|| building.getBuildingTotalFlats().isEmpty() || building.getBuildingTotalFlats()==null){
            Building building1=building_repo.findById(buildId).orElseThrow(()->new RecordNotFoundException("Record with the given ID is not Present : "+buildId));
            building1.setDeleted(true);
            building_repo.save(building1);
        }
        else{
            throw new BuildingHasFlatsException("Building with active flats cannot be deleted.");}
        return BuildingMapper.toDTO(building);
    }

    @Override
    public BuildingResponseDTO updateBuilding(String buildId, BuildingRequestDTO buildingRequestDTO) {
        Building building=BuildingMapper.toEntity(buildingRequestDTO);

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
        return BuildingMapper.toDTO(saved);
    }

    @Override
    public  List<BuildingResponseDTO>getBuildingsByOwnerId(String ownerId){
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        if (ownerBuildings.isEmpty()) {
            throw new RecordNotFoundException(
                    "No buildings found for owner with id: " + ownerId);
        }
        return ownerBuildings.stream().map(build->BuildingMapper.toDTO(build)).collect(Collectors.toList());
    }
}
