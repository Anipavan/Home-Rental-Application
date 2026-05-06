package com.spa.home_rental_application.auth_service.Dto.Response;

/**
 * Standard auth response: access JWT + opaque refresh token. Returned by
 * /auth/login and /auth/refresh. Carries the auth-side numeric user id
 * (as a String) so the SPA can scope owner/tenant queries without a
 * follow-up lookup call.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long   accessTokenExpiresInSeconds,
        String userName,
        String authUserId,
        String role
) {
    public static AuthResponse bearer(String access, String refresh, long ttl,
                                      String userName, String authUserId, String role) {
        return new AuthResponse(access, refresh, "Bearer", ttl, userName, authUserId, role);
    }
}
