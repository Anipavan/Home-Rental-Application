package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /auth/me/role} — the Welcome-page "what brings
 * you here?" flow. Users pick between TENANT (renting) and OWNER
 * (listing property / running a society). MAINTAINER + ADMIN are
 * rejected at the service layer — those require a claim approval
 * or admin grant, not self-attestation.
 */
public record SetPrimaryRoleRequest(
        @NotBlank(message = "role is mandatory")
        @Pattern(regexp = "TENANT|OWNER",
                message = "role must be TENANT or OWNER (MAINTAINER/ADMIN require an approval path)")
        String role
) {}
