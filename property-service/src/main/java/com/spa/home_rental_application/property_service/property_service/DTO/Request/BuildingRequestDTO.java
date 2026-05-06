package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request payload to create or update a Building.
 *
 * Business rules enforced here (client gets a clean 400 with field-level
 * messages instead of a 500 from a downstream NPE):
 *   - Every building MUST be tied to an ownerId (no orphan listings)
 *   - Total flats must be 6 - 20 inclusive
 *   - Total floors must be at least 1
 */
@Builder
public record BuildingRequestDTO(
        @NotBlank(message = "Building name is required")
        String buildingName,

        @NotBlank(message = "Owner is required -- a building cannot be listed without an owner")
        String ownerId,

        @NotBlank(message = "Address is required")
        String buildingAddress,

        @NotBlank(message = "City is required")
        String buildingCity,

        @NotBlank(message = "State is required")
        String buildingState,

        @NotNull(message = "Total floors is required")
        @Min(value = 1, message = "Floors must be at least 1")
        Integer buildingTotalFloors,

        @NotNull(message = "Total flats is required")
        @Min(value = 6,  message = "A building must have at least 6 flats")
        @Max(value = 20, message = "A building cannot have more than 20 flats")
        Integer buildingTotalFlats,

        String amenities
) {}
