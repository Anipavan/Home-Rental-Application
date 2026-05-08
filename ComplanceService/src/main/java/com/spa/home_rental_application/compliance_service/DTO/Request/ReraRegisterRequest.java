package com.spa.home_rental_application.compliance_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record ReraRegisterRequest(
        @NotBlank String propertyId,
        @NotBlank String ownerId,
        @NotBlank String state,
        String reraPortalId,             // optional pre-registered portal id (some states use it)
        String additionalNotes
) {
}
