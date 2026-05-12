package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
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

/**
 * REST endpoints for Flat lifecycle management.
 * All paths are mounted under /properties so the API Gateway can route
 * /rentals/v1/properties/** (and the deprecated /api/properties/**) to
 * this service after stripping the prefix.
 */
@RestController
@RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Flats", description = "Flat lifecycle: create, query, assign, vacate")
public class FlatController {

    private final FlatService flatService;

    public FlatController(FlatService flatService) {
        this.flatService = flatService;
    }

    @Operation(summary = "List active flats (paginated)")
    @GetMapping("/flats")
    public ResponseEntity<Page<FlatResponseDTO>> getAllFlats(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // M11: cap size at 100 to defang ?size=99999 DoS.
            @RequestParam(defaultValue = "10") @Min(1) @jakarta.validation.constraints.Max(100) int size) {
        log.info("GET /properties/flats page={} size={}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(flatService.getAllFlats(pageable));
    }

    @Operation(summary = "Get flat by ID")
    @GetMapping("/flats/{flatId}")
    public ResponseEntity<FlatResponseDTO> getFlatById(@PathVariable String flatId) {
        log.info("GET /properties/flats/{}", flatId);
        return ResponseEntity.ok(flatService.getflatById(flatId));
    }

    @Operation(summary = "Create a new flat")
    @PostMapping(
            value = "/flats/create/flat",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlatResponseDTO> createFlat(@RequestBody @Valid FlatRequestDTO flatRequestDTO) {
        log.info("POST /properties/flats/create/flat body={}", flatRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(flatService.createFlat(flatRequestDTO));
    }

    @Operation(summary = "Soft-delete a flat")
    @DeleteMapping("/flats/{flatId}")
    public ResponseEntity<FlatResponseDTO> deleteFlat(@PathVariable String flatId) {
        log.info("DELETE /properties/flats/{}", flatId);
        return ResponseEntity.ok(flatService.deleteFlatById(flatId));
    }

    @Operation(summary = "Get all flats in a building")
    @GetMapping("/flats/building/{buildId}")
    public ResponseEntity<List<FlatResponseDTO>> getFlatsByBuildingId(@PathVariable String buildId) {
        return ResponseEntity.ok(flatService.getflatsByBuildingId(buildId));
    }

    @Operation(summary = "Get the flat(s) assigned to a tenant -- powers the tenant 'My flat' view")
    @GetMapping("/flats/tenant/{tenantId}")
    public ResponseEntity<List<FlatResponseDTO>> getFlatsByTenant(@PathVariable String tenantId) {
        log.info("GET /properties/flats/tenant/{}", tenantId);
        return ResponseEntity.ok(flatService.getflatsByTenantId(tenantId));
    }

    @Operation(summary = "List all currently vacant flats")
    @GetMapping("/flats/vacant")
    public ResponseEntity<List<FlatResponseDTO>> getVacantFlats() {
        return ResponseEntity.ok(flatService.getAllVacentFlats());
    }

    /**
     * "Near me" geosearch: flats whose parent building has a geo-pin
     * within {@code radiusKm} of (lat, lng). Computed via the
     * Haversine great-circle formula — accurate enough for rental
     * radius queries (sub-200km), zero new dependencies.
     *
     * <p>Buildings without coordinates are excluded; the future map
     * view should show them as a city-centroid fallback if the
     * non-geo filter set is otherwise empty.
     *
     * <p>Public GET — the gateway already opens
     * {@code GET /rentals/v1/properties/flats/**} for anonymous
     * browse, so a logged-out visitor on the public site can run
     * "show me flats near my current location" without signing in.
     */
    @Operation(summary = "Flats within {radiusKm} of (lat, lng) — Haversine distance")
    @GetMapping("/flats/near")
    public ResponseEntity<List<FlatResponseDTO>> nearFlats(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        // Audit L1: validate the inputs at the boundary. Without this,
        // a client could pass lat=999 / lng=-999 / radiusKm=-100 and
        // the Haversine math would happily return garbage (or, worse,
        // the radius=Double.MAX_VALUE case would full-scan the
        // catalog). 400 with a clear message is the right shape — the
        // backend has no graceful fallback for nonsensical geometry.
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException(
                    "lat must be a finite number in [-90, 90]; got " + lat);
        }
        if (!Double.isFinite(lng) || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException(
                    "lng must be a finite number in [-180, 180]; got " + lng);
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0.0 || radiusKm > 500.0) {
            throw new IllegalArgumentException(
                    "radiusKm must be in (0, 500]; got " + radiusKm);
        }
        log.info("GET /properties/flats/near lat={} lng={} radiusKm={}",
                lat, lng, radiusKm);
        return ResponseEntity.ok(flatService.findFlatsNear(lat, lng, radiusKm));
    }

    @Operation(summary = "Mark a flat as vacant (publishes flat.vacated)")
    @PostMapping("/flats/{flatId}/vacate")
    public ResponseEntity<FlatResponseDTO> vacateFlat(@PathVariable String flatId) {
        log.info("POST /properties/flats/{}/vacate", flatId);
        return ResponseEntity.ok(flatService.makeFlatVacate(flatId));
    }

    @Operation(summary = "Update flat attributes")
    @PutMapping("/flats/{flatId}")
    public ResponseEntity<FlatResponseDTO> updateFlat(@PathVariable String flatId,
                                                      @RequestBody @Valid FlatRequestDTO flatRequestDTO) {
        log.info("PUT /properties/flats/{}", flatId);
        return ResponseEntity.ok(flatService.updateFlat(flatId, flatRequestDTO));
    }

    @Operation(summary = "Assign a tenant to a flat (publishes flat.occupied)")
    @PostMapping(value = "/flats/{flatId}/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlatResponseDTO> assignFlat(@PathVariable String flatId,
                                                      @RequestBody @Valid AssignFlatRequest body) {
        log.info("POST /properties/flats/{}/assign tenant={}", flatId, body.tenantId());
        return ResponseEntity.ok(flatService.assignFlat(flatId, body));
    }
}
