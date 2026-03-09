package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.time.LocalDateTime;

public record BuildingResponseDTO (
        String buildingId,
        String buildingName,
        String ownerId,
        String buildingAddress,
        String buildingCity,
         String buildingState,
        Integer buildingTotalFloors,
         Integer buildingTotalFlats,
        String amenities,
        LocalDateTime createdDt,
        LocalDateTime updatedDt
){ }
