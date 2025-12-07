package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import jakarta.ws.rs.Produces;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/properties",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Slf4j
public class BuildingsController {
    @Autowired
    BuildingService building_service;

    @GetMapping("/buildings")
    public List<Building> getAllBuildings() {
        log.info("Fetch all buildings");
        return building_service.getBuildings();
    }

    @PostMapping(
            value = "/create/building",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Building createBuilding(@RequestBody Building building) {
        log.info("Request recieved for creating building.{}",building);
        return building_service.createBuilding(building);
    }
}
