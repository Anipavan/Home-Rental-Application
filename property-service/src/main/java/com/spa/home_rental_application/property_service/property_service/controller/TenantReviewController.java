package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateTenantReviewRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.TenantReviewResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.TenantReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/properties/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Tenant Reviews", description = "Owner ratings of tenants after a tenancy")
public class TenantReviewController {

    private final TenantReviewService service;

    public TenantReviewController(TenantReviewService service) { this.service = service; }

    @Operation(summary = "Owner creates a review of a tenant -- 1-5 star + optional comment")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantReviewResponseDTO> create(@RequestBody @Valid CreateTenantReviewRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
    }

    @Operation(summary = "All reviews received by a tenant (across owners)")
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<TenantReviewResponseDTO>> forTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(service.forTenant(tenantId));
    }

    @Operation(summary = "All reviews written by an owner")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<TenantReviewResponseDTO>> forOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(service.forOwner(ownerId));
    }

    @Operation(summary = "All reviews tied to a specific flat")
    @GetMapping("/flat/{flatId}")
    public ResponseEntity<List<TenantReviewResponseDTO>> forFlat(@PathVariable String flatId) {
        return ResponseEntity.ok(service.forFlat(flatId));
    }
}
