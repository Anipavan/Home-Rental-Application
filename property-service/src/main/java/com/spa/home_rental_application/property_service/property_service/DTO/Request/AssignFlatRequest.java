package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Body for POST /properties/flats/{id}/assign — assigns a tenant to a flat
 * and seeds the lease window. Triggers the flat.occupied Kafka event.
 */
public record AssignFlatRequest(
        @NotBlank(message = "tenantId is required")
        String tenantId,

        @NotNull(message = "leaseStartDate is required")
        LocalDate leaseStartDate,

        @NotNull(message = "leaseEndDate is required")
        LocalDate leaseEndDate
) {}
