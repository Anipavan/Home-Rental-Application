package com.spa.home_rental_application.compliance_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReraRegistrationResponseDto(
        String id,
        String propertyId,
        String ownerId,
        String state,
        String reraRegistrationNumber,
        String reraPortalId,
        String registrationStatus,
        LocalDateTime registeredAt,
        LocalDate expiryDate,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
