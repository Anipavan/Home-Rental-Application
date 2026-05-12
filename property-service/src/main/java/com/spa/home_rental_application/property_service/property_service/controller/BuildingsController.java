package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Buildings", description = "Building catalog: CRUD, owner-scoped lookup, soft delete")
public class BuildingsController {

    private final BuildingService building_service;

    public BuildingsController(BuildingService service) {
        this.building_service = service;
    }

    @Operation(summary = "List active buildings (paginated)")
    @GetMapping("/buildings")
    public ResponseEntity<Page<BuildingResponseDTO>> getAllBuildings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Audit M11 originally capped size at 100 to stop ?size=999999
            // abuse. Bumped to 500 because the map view (browse.tsx → "Map"
            // toggle) legitimately needs to render every active building's
            // lat/lng pin at once and was calling /buildings?size=200 —
            // 100 rejected that with a 400, breaking the map with a
            // "Couldn't load the map data" error. 500 is still small
            // enough to bound payload size (each BuildingResponseDTO is
            // ~1 KB → ~500 KB worst case) while letting the map work for
            // typical city-scale catalogues. For metropolitan deployments
            // with thousands of buildings, follow up with a dedicated
            // /buildings/map endpoint that takes a bounding-box and
            // returns lat/lng-only mini-DTOs.
            @RequestParam(defaultValue = "10") @Min(1) @jakarta.validation.constraints.Max(500) int size) {
        log.info("GET /properties/buildings page={} size={}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok().body(building_service.getBuildings(pageable));
    }

    @Operation(summary = "Search active buildings by name / address / city / state")
    @GetMapping("/buildings/search")
    public ResponseEntity<List<BuildingResponseDTO>> searchBuildings(
            @RequestParam("q") String q,
            @RequestParam(value = "ownerId", required = false) String ownerId,
            @RequestParam(value = "limit", defaultValue = "10") @Min(1) int limit) {
        log.info("GET /properties/buildings/search q={} owner={} limit={}", q, ownerId, limit);
        return ResponseEntity.ok(building_service.searchBuildings(q, ownerId, limit));
    }

    @Operation(summary = "Create a new building (publishes property.created)")
    @PostMapping(
            value = "/buildings/create/building",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BuildingResponseDTO> createBuilding(@RequestBody @Valid BuildingRequestDTO buildingRequestDTO) {
        log.info("POST /properties/buildings/create/building body={}", buildingRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(building_service.createBuilding(buildingRequestDTO));
    }

    @Operation(summary = "Get building by ID")
    @GetMapping("/buildings/{buildId}")
    public ResponseEntity<BuildingResponseDTO> getBuildingById(@PathVariable String buildId) {
        log.info("GET /properties/buildings/{}", buildId);
        return ResponseEntity.ok().body(building_service.getBuildingById(buildId));
    }

    @Operation(summary = "List all buildings owned by a specific owner")
    @GetMapping("/buildings/owner/{ownerId}")
    public ResponseEntity<List<BuildingResponseDTO>> getBuildingsByOwnerId(@PathVariable String ownerId) {
        log.info("GET /properties/buildings/owner/{}", ownerId);
        return ResponseEntity.ok().body(building_service.getBuildingsByOwnerId(ownerId));
    }

    @Operation(summary = "List tenant IDs across every occupied flat in any building owned by an owner")
    @GetMapping("/buildings/owner/{ownerId}/tenants")
    public ResponseEntity<List<String>> getTenantIdsByOwner(@PathVariable String ownerId) {
        log.info("GET /properties/buildings/owner/{}/tenants", ownerId);
        return ResponseEntity.ok(building_service.getTenantIdsByOwner(ownerId));
    }

    @Operation(summary = "Soft-delete a building (only when no active flats)")
    @DeleteMapping("/buildings/{buildId}")
    public ResponseEntity<BuildingResponseDTO> deleteBuilding(@PathVariable String buildId) {
        log.info("DELETE /properties/buildings/{}", buildId);
        return ResponseEntity.ok().body(building_service.deleteBuildingById(buildId));
    }

    @Operation(summary = "Update building attributes (publishes property.updated)")
    @PutMapping("/buildings/{id}/building")
    public ResponseEntity<BuildingResponseDTO> updateBuilding(@PathVariable("id") String buildId,
                                                              @RequestBody @Valid BuildingRequestDTO buildingRequestDTO) {
        log.info("PUT /properties/buildings/{}/building", buildId);
        return ResponseEntity.ok().body(building_service.updateBuilding(buildId, buildingRequestDTO));
    }
}
