package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response payload for a Flat. Embeds a small slice of the parent
 * Building (name + address + city) so single-flat views don't need a
 * second API call. The building* fields are nullable for the rare case
 * where the parent has been hard-deleted.
 */
public record FlatResponseDTO(
        String id,
        String buildingId,
        String buildingName,
        String buildingAddress,
        String buildingCity,
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
