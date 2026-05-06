package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.*;

public record CreateTenantReviewRequest(
        @NotBlank String ownerId,
        @NotBlank String tenantId,
        @NotBlank String flatId,
        String buildingId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment
) {}
