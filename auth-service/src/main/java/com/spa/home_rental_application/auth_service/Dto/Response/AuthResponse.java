package com.spa.home_rental_application.auth_service.Dto.Response;

import java.util.List;

/**
 * Standard auth response: access JWT + opaque refresh token. Returned by
 * /auth/login and /auth/refresh. Carries the auth-side numeric user id
 * (as a String) so the SPA can scope owner/tenant queries without a
 * follow-up lookup call.
 *
 * <p>V17 — adds {@code roles}: the multi-role union. Existing clients
 * keep reading {@code role} (their primary role); new code can use
 * {@code roles} to render a role switcher / multi-role permission
 * checks without an extra API call.
 */
public record AuthResponse(
        String       accessToken,
        String       refreshToken,
        String       tokenType,
        long         accessTokenExpiresInSeconds,
        String       userName,
        String       authUserId,
        String       role,
        List<String> roles
) {
    public static AuthResponse bearer(String access, String refresh, long ttl,
                                      String userName, String authUserId,
                                      String role, List<String> roles) {
        return new AuthResponse(access, refresh, "Bearer", ttl,
                userName, authUserId, role, roles);
    }
}
