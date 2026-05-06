package com.spa.home_rental_application.auth_service.Dto.Response;

import java.time.Instant;

/**
 * Public-facing projection of UserDetails for /auth/role queries
 * and inter-service Feign joins. Excludes password and any non-public field.
 * <p>
 * Field names are kept compatible with the legacy {@code authResponseDto}
 * used by User Service (id, userName, userRole, recordCreatedDate, recodeUpdatedDate).
 */
public record AuthUserResponse(
        String  id,
        String  userName,
        String  userRole,
        String  email,
        Instant recordCreatedDate,
        Instant recodeUpdatedDate
) {}
