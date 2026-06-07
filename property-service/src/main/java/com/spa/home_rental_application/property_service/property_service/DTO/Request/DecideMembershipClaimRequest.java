package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /society/claims/{id}/approve} and
 * {@code PUT /society/claims/{id}/reject}. Both take an optional
 * note the owner adds — shown back to the claimant when their
 * claim resolves. Empty body is fine for the no-comment case.
 */
public record DecideMembershipClaimRequest(
        @Size(max = 500, message = "Note must be 500 characters or less")
        String decisionNote
) {
}
