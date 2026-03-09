package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
@Builder
public record BuildingRequestDTO (
        @NotBlank(message = "Building name is required")
        String buildingName,

        String ownerId,

        @NotBlank(message = "Address is required")
        String buildingAddress,

@NotBlank(message = "City is required")
 String buildingCity,
@NotBlank(message = "State is required")
 String buildingState,
@NotNull(message = "Total floors is required")
@Positive(message = "Floors must be greater than 0")
 Integer buildingTotalFloors,
@NotNull(message = "Total flats is required")
@Positive(message = "Flats must be greater than 0")
 Integer buildingTotalFlats,
 String amenities){}
