package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.enums.Roles;

import java.util.List;

public interface AuthService {
    RegisterResponse register(RegisterRequest req);
    AuthResponse     login(LoginRequest req, String ipAddress, String userAgent);
    /**
     * Rotate the supplied refresh token. The IP + user-agent are
     * compared against the values stored at issue-time to detect
     * stolen-token replay (H5). Pass nulls to bypass the fingerprint
     * check entirely (e.g. internal callers).
     */
    AuthResponse     refresh(RefreshTokenRequest req, String ipAddress, String userAgent);
    void             logout(LogoutRequest req);
    void             startPasswordReset(ForgotPasswordRequest req);
    void             completePasswordReset(ResetPasswordRequest req);
    List<AuthUserResponse> getUsersByRole(Roles role);
    AuthUserResponse getById(Long id);

    /**
     * H3: per-user revoked-before watermark. Null = never revoked.
     * Looked up by the api-gateway JWT validator (with a Caffeine
     * cache) to enforce immediate logout.
     */
    java.time.Instant tokensRevokedBefore(Long userId);
}
