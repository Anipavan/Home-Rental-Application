package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BuildingsController {

   private final BuildingService building_service;


    private BuildingsController(BuildingService service)
    {
        this.building_service=service;
    }

    @GetMapping("/buildings")
    public List<Building> getAllBuildings() {
        log.info("Request received to fetch all Buildings");
        return building_service.getBuildings();
    }

    @PostMapping(
            value = "/buildings/create/building",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Building createBuilding(@RequestBody Building building) {
        log.info("Request received for creating building.{}",building);
        return building_service.createBuilding(building);
    }

    @GetMapping("/buildings/{buildId}")
    public Building getBuildingById(@PathVariable String buildId) {
        log.info("Request received to fetch building by ID : {}",buildId);
        return building_service.getBuildingById(buildId);
    }
    @GetMapping("/buildings/owner/{ownerId}")
    public List<Building> getBuildingsByOwnerId(@PathVariable String ownerId) {
        return building_service.getBuildingsByOwnerId(ownerId);
    }

    @DeleteMapping("/buildings/{buildId}")
    public ResponseEntity<String> deleteBuilding(@PathVariable String buildId) {
        String message = building_service.deleteBuildingById(buildId);
        return ResponseEntity.ok(message);
    }

    @PutMapping("/buildings/{id}/building")
    public Building updateBuilding(@PathVariable String buildId,Building building){
        return  building_service.updateBuilding(buildId,building);
    }
}
