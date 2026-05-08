package com.spa.home_rental_application.lease_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseHistoryDto(
        String id,
        String leaseId,
        String eventType,
        BigDecimal previousRent,
        BigDecimal newRent,
        String changedBy,
        String notes,
        LocalDateTime changedAt
) {
}
