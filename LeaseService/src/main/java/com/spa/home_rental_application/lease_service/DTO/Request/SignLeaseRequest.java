package com.spa.home_rental_application.lease_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record SignLeaseRequest(
        @NotBlank String signatureProvider,    // DIGIO | DOCUSIGN | MOCK
        @NotBlank String signedBy              // userId of signer
) {
}
