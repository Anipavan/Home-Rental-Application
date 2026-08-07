package com.spa.home_rental_application.auth_service.Dto.Request;

/**
 * {@code refreshToken} is now optional — cookie is the primary source.
 * Body is only used by non-browser clients (Postman scripts, other
 * services) that don't have the {@code hra_refresh} cookie. If both
 * are absent, /auth/logout is a no-op (server-side session cleared
 * only when it can identify the refresh token to revoke).
 */
public record LogoutRequest(
        String refreshToken
) {}
