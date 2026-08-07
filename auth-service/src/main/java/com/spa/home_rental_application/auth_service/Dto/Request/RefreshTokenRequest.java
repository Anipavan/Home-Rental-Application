package com.spa.home_rental_application.auth_service.Dto.Request;

/**
 * {@code refreshToken} is now optional in the request body — the
 * primary source is the {@code hra_refresh} HttpOnly cookie set on
 * login. Body-supplied token is still accepted for backward compat
 * with any external / non-browser client that hasn't migrated to the
 * cookie flow. Browsers send the cookie automatically.
 */
public record RefreshTokenRequest(
        String refreshToken
) {}
