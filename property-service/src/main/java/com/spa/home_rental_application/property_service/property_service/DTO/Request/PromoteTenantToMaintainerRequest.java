package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Owner-initiated promote: convert an existing tenant of one of the
 * building's flats into the society's MAINTAINER. Two side-effects
 * across service boundaries:
 *
 * <ol>
 *   <li><b>auth-service:</b> bump the user's role to MAINTAINER and
 *       reset their password to {@code temporaryPassword}. The user
 *       changes it on first login.</li>
 *   <li><b>property-service:</b> set
 *       {@code BuildingSocietyConfig.maintainerUserId = tenantUserId}.</li>
 * </ol>
 *
 * <p>The password rule mirrors auth-service's RegisterRequest validator
 * (at least 8 chars, one upper, one lower, one digit) so the user can
 * actually log in with what the owner sets. A weaker password would
 * succeed here and fail on the first login attempt, which is the
 * worst possible UX.
 *
 * <p>The tenantUserId must be one of the {@code authUserId}s returned
 * by {@code GET /society/{buildingId}/eligible-maintainers}; the
 * service layer validates the mapping (flat exists, belongs to this
 * building, tenantId on the flat matches the supplied id).
 */
public record PromoteTenantToMaintainerRequest(
        @NotBlank(message = "tenantUserId is required")
        String tenantUserId,

        @NotBlank(message = "Temporary password is required")
        @Size(min = 8, max = 64, message = "Temporary password must be 8-64 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$",
                message = "Temporary password must contain at least one uppercase, one lowercase, and one digit"
        )
        String temporaryPassword
) {
}
