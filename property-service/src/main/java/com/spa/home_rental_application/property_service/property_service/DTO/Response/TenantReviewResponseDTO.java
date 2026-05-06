package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.time.LocalDateTime;

public record TenantReviewResponseDTO(
        String id,
        String ownerId,
        String tenantId,
        String flatId,
        String buildingId,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
