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
        Instant      recodeUpdatedDate
) {}
