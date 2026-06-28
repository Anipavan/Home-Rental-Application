package com.spa.home_rental_application.auth_service.Dto.Response;

import java.time.Instant;
import java.util.List;

/**
 * Returned from POST /auth/register. Never includes the password hash.
 * V17 — {@code roles} is the multi-role union; {@code role} stays as
 * the primary role for backwards compatibility with the SPA's existing
 * post-signup routing logic.
 */
public record RegisterResponse(
        String       authUserId,
        String       userName,
        String       email,
        String       role,
        List<String> roles,
        Instant      recordCreatedDate
) {}
