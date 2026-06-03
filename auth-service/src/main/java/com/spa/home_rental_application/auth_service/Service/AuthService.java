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

    /**
     * Owner-driven flow: an existing tenant of a building's flats is
     * promoted to MAINTAINER of the society. Property-service is the
     * only legitimate caller (it validates building-ownership before
     * issuing this call) — hence the endpoint sits under
     * {@code /auth/internal/**} guarded by the gateway HMAC.
     *
     * <p>Side-effects:
     * <ul>
     *   <li>{@code userRole} flipped to MAINTAINER (no-op if already).</li>
     *   <li>{@code userPassword} replaced with a BCrypt hash of
     *       {@code newPassword}. The user changes it on first login.</li>
     *   <li>{@code tokensRevokedBefore} bumped to {@code now()} so any
     *       access JWT the user currently holds dies immediately. They
     *       must re-login with the temp credentials.</li>
     *   <li>{@code accountNonLocked} reset to true + failedLoginAttempts
     *       cleared — covers the corner case where the user happened
     *       to be in the 15-min lockout window when the owner
     *       promoted them.</li>
     * </ul>
     *
     * <p>Idempotent: re-running with the same {@code authUserId} and
     * a fresh password simply resets the password again. We do NOT
     * publish a "role-changed" Kafka event today — the maintainer
     * dashboard inheriting OWNER capabilities is purely a UI / API
     * permission shift; no downstream consumer cares.
     */
    AuthUserResponse promoteToMaintainer(Long authUserId, String newPassword);
}
