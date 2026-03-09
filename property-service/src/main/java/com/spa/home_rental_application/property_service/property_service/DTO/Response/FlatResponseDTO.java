package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FlatResponseDTO (
        String id,
        String buildingId,
        String flatNumber,
        Integer floor,
        Integer bedrooms,
        Integer bathrooms,
        Double areaSqft,
        BigDecimal rentAmount,
        Boolean isOccupied,
        String tenantId,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
