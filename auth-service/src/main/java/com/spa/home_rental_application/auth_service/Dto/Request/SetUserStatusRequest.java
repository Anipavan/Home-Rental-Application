package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code PATCH /auth/users/{id}/status} — the
 * admin-only endpoint that enables or disables a user account.
 *
 * <p>{@code enabled} is required. {@code reason} is optional free-text
 * (max 60 chars to fit the {@code disable_reason} column). Passing
 * {@code enabled=true} clears the reason regardless of what was
 * supplied.
 */
public record SetUserStatusRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled,

        @Size(max = 60, message = "reason must be 60 characters or fewer")
        String reason
) {
}
