package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import com.spa.home_rental_application.property_service.property_service.utils.PropertyEventProducer;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.PropertyUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BuildingImpul implements BuildingService {
    private final BuildingRepo building_repo;
    private final PropertyEventProducer eventProducer;

    public BuildingImpul(BuildingRepo building_repo, PropertyEventProducer eventProducer) {
        this.building_repo = building_repo;
        this.eventProducer = eventProducer;
    }
    @Override
    public List<Building> getBuildings() {
        return  building_repo.findAll();
    }

    @Override
    public Building createBuilding(Building building) {
        log.info("Implimentation of building request.");

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
        return saved;
    }
    @Override
    public Building getBuildingById(String buildId)
    {
        Building building=building_repo.findById(buildId).orElseThrow(
                ()-> new RecordNotFoundException("No Record found with the given id :"+buildId));
       return building;
    }

    @Override
    public String deleteBuildingById(String buildId)
    {
         Building building=building_repo.findById(buildId).orElseThrow(() -> new RecordNotFoundException("No record found with the given id: " + buildId));
         if(building.getBuildingTotalFlats().isBlank()|| building.getBuildingTotalFlats().isEmpty() || building.getBuildingTotalFlats()==null){
             building_repo.deleteById(buildId);}
         else{
             throw new BuildingHasFlatsException("Building with active flats cannot be deleted.");}
        return "Record with id " + buildId + " has been deleted successfully.";
    }

    @Override
    public Building updateBuilding(String buildId, Building building) {

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
        return saved;
    }

    @Override
    public  List<Building>getBuildingsByOwnerId(String ownerId){
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        if (ownerBuildings.isEmpty()) {
            throw new RecordNotFoundException(
                    "No buildings found for owner with id: " + ownerId);
        }
    return ownerBuildings;
    }
}
