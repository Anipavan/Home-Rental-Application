package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/** Tenant-supplied signature payload. signatureData is a base64-encoded PNG. */
public record SignAgreementRequest(
        @NotBlank(message = "Signature image is required")
        String signatureData
) {}
