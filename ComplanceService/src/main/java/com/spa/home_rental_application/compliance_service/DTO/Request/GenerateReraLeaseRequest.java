package com.spa.home_rental_application.compliance_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload to generate a RERA-compliant lease deed PDF for a signed lease.
 * The Lease Service owns the lease record; we only stamp it with the
 * relevant RERA registration number for the state.
 */
public record GenerateReraLeaseRequest(
        @NotBlank String leaseId,
        @NotBlank String propertyId,
        @NotBlank String state
) {
}
