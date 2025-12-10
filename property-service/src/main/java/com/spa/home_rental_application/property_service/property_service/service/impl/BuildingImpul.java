package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BuildingImpul implements BuildingService {
    @Autowired
    BuildingRepo building_repo;

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

        return building_repo.save(building);
    }
    @Override
    public Building getBuildingById(String buildId)
    {
       return building_repo.findById(buildId).orElseThrow(()-> new RecordNotFoundException("No Record found with the given id :"+buildId));
    }

    @Override
    public String deleteBuildingById(String buildId)
    {
         building_repo.deleteById(buildId);
         return "Successfully deleted";
    }
}
