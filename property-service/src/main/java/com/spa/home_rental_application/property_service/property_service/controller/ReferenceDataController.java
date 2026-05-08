package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.CityResponseDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.StateResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reference data endpoints — states and cities for the Add Building UI.
 *
 * <p>Mounted at {@code /properties/reference/**}; gateway exposes them at
 * {@code /rentals/v1/properties/reference/**}.
 */
@RestController
@RequestMapping(value = "/properties/reference", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Reference data", description = "States + cities lookup for cascading dropdowns")
public class ReferenceDataController {

    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    @Operation(summary = "All Indian states + UTs, alphabetically.")
    @GetMapping("/states")
    public ResponseEntity<List<StateResponseDTO>> states() {
        return ResponseEntity.ok(service.listStates());
    }

    @Operation(summary = "Cities within a given state, alphabetically.")
    @GetMapping("/cities")
    public ResponseEntity<List<CityResponseDTO>> citiesByState(
            @RequestParam("stateId") Long stateId) {
        return ResponseEntity.ok(service.citiesForState(stateId));
    }

    @Operation(summary = "Free-text city auto-suggest. Min 2 chars, max 25 results.")
    @GetMapping("/cities/search")
    public ResponseEntity<List<CityResponseDTO>> searchCities(@RequestParam("q") String q) {
        return ResponseEntity.ok(service.searchCities(q));
    }
}
