package com.spa.home_rental_application.auth_service.Dto.Response;

import java.time.Instant;

/**
 * Returned from POST /auth/register. Never includes the password hash.
 */
public record RegisterResponse(
        String  authUserId,
        String  userName,
        String  email,
        String  role,
        Instant recordCreatedDate
) {}
