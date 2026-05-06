package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
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
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Owners", description = "Owner profile management and tenant lookup")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @Operation(summary = "Create an owner profile (publishes owner.registered)")
    @PostMapping(value = "/owners", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnerResponseDto> createOwner(@Valid @RequestBody OwnerRequestDto ownerRequest) {
        log.info("POST /users/owners userId={} businessName={}", ownerRequest.userId(), ownerRequest.businessName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ownerService.createOwner(ownerRequest));
    }

    @Operation(summary = "List all owners (paginated)")
    @GetMapping("/owners")
    public ResponseEntity<Page<OwnerResponseDto>> getAllOwners(
            @RequestParam(defaultValue = "0") @Min(0) int pagenum,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable pageable = PageRequest.of(pagenum, size);
        return ResponseEntity.ok(ownerService.getAllOwners(pageable));
    }

    @Operation(summary = "Get owner by id")
    @GetMapping("/owners/{ownerId}")
    public ResponseEntity<OwnerResponseDto> getOwnerById(@PathVariable String ownerId) {
        return ResponseEntity.ok(ownerService.getOwnerById(ownerId));
    }

    @Operation(summary = "List tenants of an owner (joins on Property Service via Feign)")
    @GetMapping("/owners/{ownerId}/tenants")
    public ResponseEntity<List<UserResponseDto>> getTenantsByOwnerId(@PathVariable String ownerId) {
        return ResponseEntity.ok(ownerService.getTenentsByOwnerId(ownerId));
    }

    @Operation(summary = "Update an owner profile")
    @PutMapping(value = "/owners/{ownerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnerResponseDto> updateOwner(
            @PathVariable String ownerId,
            @Valid @RequestBody OwnerRequestDto ownerRequest) {
        return ResponseEntity.ok(ownerService.updateOwner(ownerId, ownerRequest));
    }
}
