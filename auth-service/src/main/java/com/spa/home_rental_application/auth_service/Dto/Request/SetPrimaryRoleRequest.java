package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /auth/me/role} — the Welcome-page "what brings
 * you here?" flow. Users self-attest into TENANT, OWNER, or MAINTAINER.
 *
 * <p>Phase 5: MAINTAINER is now self-attestable (same trust model as
 * OWNER — the platform trusts users to correctly identify their own
 * intent; disputes go through admin). A user who picks MAINTAINER
 * still needs to register their society building via
 * {@code POST /society/buildings/register-as-maintainer} before they
 * can approve maintainee join requests.
 *
 * <p>ADMIN remains admin-granted only.
 */
public record SetPrimaryRoleRequest(
        @NotBlank(message = "role is mandatory")
        @Pattern(regexp = "TENANT|OWNER|MAINTAINER",
                message = "role must be TENANT, OWNER, or MAINTAINER (ADMIN is admin-granted)")
        String role
) {}
