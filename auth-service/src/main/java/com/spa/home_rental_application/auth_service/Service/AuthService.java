package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.MaintainerPaymentStatusResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterPendingResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.enums.Roles;

import java.util.List;

public interface AuthService {
    RegisterResponse register(RegisterRequest req);

    /**
     * Paid maintainer signup — entry point for the "I'm a maintainer"
     * card on the public register screen. Persists the auth row as
     * {@code enabled=false, disable_reason='REGISTRATION_PAYMENT_PENDING'},
     * forwards the profile to user-service (same as
     * {@link #register}), and asks payment-service to mint a PENDING
     * Payment row for the &#8377;999 fee. Returns the payment bundle
     * the frontend uses to launch the Razorpay paywall.
     *
     * <p>The {@code user.registered} Kafka event is <em>not</em> fired
     * here — only on a successful {@link #activateRegistration}, so
     * downstream consumers see the user only after they've actually
     * paid. The orphan-row sweep on auth-service deletes disabled
     * rows older than 24 hours that never made it to activation.
     */
    RegisterPendingResponse registerPending(RegisterPendingRequest req);

    /**
     * Internal endpoint hit by payment-service via Feign on Razorpay
     * PAID for a {@code MAINTAINER_REGISTRATION} payment. Flips
     * {@code enabled=true, disable_reason=null} on the user row and
     * fires the deferred {@code user.registered} event so welcome
     * fan-outs (email + SMS + bell) trigger as if the user had just
     * signed up the normal free way.
     *
     * <p>Idempotent: if the row is already enabled, returns the
     * existing record without re-firing events. That way a retry
     * from payment-service's {@code RegistrationActivationReconciler}
     * scheduler can't double-notify the user.
     */
    AuthUserResponse activateRegistration(Long authUserId, String paymentId);
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

    /**
     * Self-service variant of {@link #promoteToMaintainer} used when an
     * owner approves a self-registered membership claim. The user
     * already picked their own password at signup, so we DO NOT
     * touch the password column or invalidate their existing sessions
     * — we simply bump {@code userRole} to MAINTAINER.
     *
     * <p>Refuses to change ADMIN users (same as the password-changing
     * sibling). OWNER users keep their OWNER role (idempotent no-op)
     * because OWNER already implies the same capabilities as
     * MAINTAINER in this codebase's permission model; demoting them
     * would be a regression.
     */
    AuthUserResponse grantMaintainerRole(Long authUserId);

    /* ---------- Maintainer-payment soft gate ---------- */

    /**
     * Evaluate the maintainer-payment state machine for the
     * authenticated user. Delegates to
     * {@link SystemSettingsService#computeStatus}. Cached values are
     * preferred — the per-page refetch from the frontend
     * (MaintainerPaymentGate) goes through a 60 s cache on the
     * toggle read.
     */
    MaintainerPaymentStatusResponse getPaymentStatus(Long authUserId);

    /**
     * Record a "Skip for 4 more days" click. Increments
     * payment_skip_count + stamps payment_last_skip_at=now. Refuses
     * if the user isn't in the PROMPT state — this prevents the
     * client from sneaking past mandatory by spamming the endpoint.
     */
    MaintainerPaymentStatusResponse recordPaymentSkip(Long authUserId);

    /**
     * Logged-in counterpart to the anonymous
     * {@link #registerPending}. Mints a PENDING Payment row on
     * payment-service and a fresh 30-min REG_PAY token for the
     * caller, so the dashboard payment modal can route them into
     * the existing /registration-payment paywall page.
     */
    RegisterPendingResponse initiateOwnPayment(Long authUserId);

    /* ---------- Phase 4: unified-signup role selection ---------- */

    /**
     * Self-service primary-role change. Backs the {@code /welcome}
     * "what brings you here?" screen that new signups land on. Only
     * TENANT ↔ OWNER transitions are permitted — MAINTAINER requires
     * the existing claim-approval flow and ADMIN is admin-granted.
     *
     * <p>Side-effects:
     * <ul>
     *   <li>{@code userRole} updated to the requested value</li>
     *   <li>The new role is added to {@code userRoles} (Phase 3
     *       multi-role set) so the user keeps both roles going
     *       forward — a TENANT who upgrades to OWNER can still
     *       browse listings on their TENANT dashboard if desired.</li>
     *   <li>A fresh access + refresh token pair is minted with the
     *       updated authorities so the frontend immediately reflects
     *       the new role without a re-login.</li>
     * </ul>
     */
    AuthResponse setPrimaryRole(Long authUserId, Roles newRole,
                                String ipAddress, String userAgent);
}
