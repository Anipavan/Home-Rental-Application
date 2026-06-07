package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import com.spa.home_rental_application.property_service.property_service.Entities.MembershipClaim.RequestedRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /society/claims} — a logged-in user (any role)
 * submits a claim against an existing building, asking to be added
 * as either its MAINTAINER or as the RESIDENT of a specific flat.
 *
 * <p>The {@code userId} is taken from the JWT principal at the
 * controller layer — the body does NOT carry it. {@code buildingId}
 * comes from the building picker on the signup page; we validate at
 * the service layer that the building actually exists.
 *
 * <p>{@code claimedFlatNumber} is required when role=RESIDENT
 * (without it we can't bind the user to any specific flat at
 * approval time) and optional for role=MAINTAINER (recorded for
 * context but not used in the approval write).
 */
public record CreateMembershipClaimRequest(
        @NotBlank(message = "Building is required")
        String buildingId,

        @NotNull(message = "Role is required")
        RequestedRole requestedRole,

        @Size(max = 32, message = "Flat number must be 32 characters or less")
        String claimedFlatNumber,

        @Size(max = 500, message = "Note must be 500 characters or less")
        String applicantNote
) {
}
