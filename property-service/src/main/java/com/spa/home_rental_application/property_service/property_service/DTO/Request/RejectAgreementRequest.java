package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tenant explanation for refusing an agreement. */
public record RejectAgreementRequest(
        @NotBlank(message = "A reason is required to reject an agreement")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {}
