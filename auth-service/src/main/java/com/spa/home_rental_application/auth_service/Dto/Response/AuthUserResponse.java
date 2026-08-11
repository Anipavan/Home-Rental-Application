package com.spa.home_rental_application.auth_service.Dto.Response;

import java.time.Instant;
import java.util.List;

/**
 * Public-facing projection of UserDetails for /auth/role queries
 * and inter-service Feign joins. Excludes password and any non-public field.
 * <p>
 * Field names are kept compatible with the legacy {@code authResponseDto}
 * used by User Service (id, userName, userRole, recordCreatedDate,
 * recodeUpdatedDate).
 *
 * <p>V17 — added {@code roles}: the multi-role union. Older clients
 * keep reading {@code userRole} (their stored primary role) and
 * behave exactly as before. Newer clients can switch to {@code roles}
 * to support multi-role users without a coordinated rollout.
 */
public record AuthUserResponse(
        String       id,
        String       userName,
        String       userRole,
        List<String> roles,
        String       email,
        Instant      recordCreatedDate,
        Instant      recodeUpdatedDate,
        /**
         * Account active flag — mirrors {@code UserDetails.enabled}.
         * Field is named {@code isActive} instead of {@code enabled}
         * to match the frontend's existing type definition
         * (frontend/src/types/api.ts) and the "Active" / "Disabled"
         * badge on the admin users page.
         * <p>Nullable for backward compatibility with legacy Feign
         * consumers (user-service) that were built before the field
         * existed — they read the payload with Jackson which happily
         * ignores unknown fields, and any code that reads this via
         * accessor gets {@code null} instead of a NullPointerException.
         */
        Boolean      isActive,
        /**
         * Free-text reason the account is disabled, or {@code null}
         * when active. Populated by the admin at the point of disable
         * ({@code SetUserStatusRequest.reason}) or auto-populated with
         * {@code REGISTRATION_PAYMENT_PENDING} on the maintainer
         * paywall path.
         */
        String       disableReason
) {}
