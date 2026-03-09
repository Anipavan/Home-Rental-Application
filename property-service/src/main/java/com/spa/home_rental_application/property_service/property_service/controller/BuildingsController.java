package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BuildingsController {

    private final BuildingService building_service;


    public BuildingsController(BuildingService service) {
        this.building_service = service;
    }

    @GetMapping("/buildings")
    public ResponseEntity<List<BuildingResponseDTO>> getAllBuildings() {
        log.info("Request received to fetch all Buildings");
        return ResponseEntity.ok().body(building_service.getBuildings());
    }

    @PostMapping(
            value = "/buildings/create/building",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BuildingResponseDTO> createBuilding(@RequestBody @Valid BuildingRequestDTO buildingRequestDTO) {
        log.info("Request received for creating building.{}", buildingRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(building_service.createBuilding(buildingRequestDTO));
    }

    @GetMapping("/buildings/{buildId}")
    public ResponseEntity<BuildingResponseDTO> getBuildingById(@PathVariable String buildId) {
        log.info("Request received to fetch building by ID : {}", buildId);
        return ResponseEntity.ok().body(building_service.getBuildingById(buildId));
    }

    @GetMapping("/buildings/owner/{ownerId}")
    public ResponseEntity<List<BuildingResponseDTO>> getBuildingsByOwnerId(@PathVariable String ownerId) {
        return ResponseEntity.ok().body(building_service.getBuildingsByOwnerId(ownerId));
    }

    @DeleteMapping("/buildings/{buildId}")
    public ResponseEntity<BuildingResponseDTO> deleteBuilding(@PathVariable String buildId) {

        return ResponseEntity.ok().body(building_service.deleteBuildingById(buildId));
    }

    @PutMapping("/buildings/{id}/building")
    public ResponseEntity<BuildingResponseDTO> updateBuilding(@PathVariable("id") String buildId, @RequestBody @Valid BuildingRequestDTO buildingRequestDTO) {
        return ResponseEntity.ok().body(building_service.updateBuilding(buildId, buildingRequestDTO));
    }
}
