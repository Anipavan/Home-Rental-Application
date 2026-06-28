package com.spa.home_rental_application.auth_service.Dto.Response;

/**
 * Body returned by {@code POST /auth/verify-email} on a successful
 * verification. Carries the user's email + userName so the SPA can
 * pre-fill the login form they're about to land on.
 */
public record VerifyEmailResponse(
        Long authUserId,
        String userName,
        String email,
        boolean emailVerified
) {}
