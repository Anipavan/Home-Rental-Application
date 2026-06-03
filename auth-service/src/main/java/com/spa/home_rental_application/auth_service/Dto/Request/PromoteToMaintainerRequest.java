package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code POST /auth/internal/users/{authUserId}/promote-to-maintainer}.
 *
 * <p>Only one field — the password the property-service caller (via
 * the gateway HMAC) wants set on the target user. The rest of the
 * "is this person a valid maintainer candidate" check lives in
 * property-service, which owns the building / tenant social graph.
 *
 * <p>Validation mirrors the
 * {@link RegisterRequest}-side password rule (min 8 chars, one upper,
 * one lower, one digit) so the user can actually log in with what we
 * persist. Without it, a weaker password would succeed here and fail
 * on the first login attempt — worst possible UX.
 */
public record PromoteToMaintainerRequest(
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase, one lowercase, and one digit"
        )
        String newPassword
) {
}
