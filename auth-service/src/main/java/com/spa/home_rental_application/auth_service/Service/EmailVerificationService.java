package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Entity.UserDetails;

/**
 * Issues + verifies the magic-link tokens that gate login when the
 * {@code email_verification_required} {@link
 * com.spa.home_rental_application.auth_service.Entity.SystemSetting}
 * toggle is ON.
 *
 * <p>Mint flow: the auth-service register / registerPending paths
 * call {@link #mintAndDispatch(UserDetails)} after the user row is
 * saved. The service produces a 32-byte URL-safe token, persists an
 * {@link com.spa.home_rental_application.auth_service.Entity.EmailVerificationToken}
 * row with a 24-hour expiry, and fires a Kafka event so
 * notification-service can email the link.
 *
 * <p>Verify flow: the user clicks the link, the SPA POSTs the token
 * to {@code /auth/verify-email}, which calls {@link #verify(String)}.
 * On success the user's {@code email_verified} column flips to true
 * and the token row is stamped consumed.
 *
 * <p>Resend flow: {@link #resend(String)} invalidates every still-
 * usable token for the user before minting a new one, so an old link
 * sitting in the inbox can't be replayed. Rate-limited to 3 sends per
 * user per hour.
 */
public interface EmailVerificationService {

    /**
     * Mint a fresh verification token for the user and publish the
     * {@code user.email.verification.requested} Kafka event. Caller
     * is the register / registerPending path. No-op when the user is
     * already verified (defence in depth — register has already
     * gated on this).
     */
    void mintAndDispatch(UserDetails user);

    /**
     * Look up the token, check it's usable, stamp consumed_at, flip
     * user.emailVerified=true. Returns the verified user so the
     * controller can return their email/userName in the success body.
     *
     * <p>Throws on:
     *   - unknown token (404)
     *   - expired token (410)
     *   - consumed token (410)
     */
    UserDetails verify(String rawToken);

    /**
     * Mint + dispatch a fresh token for the user with this email.
     * Idempotent on already-verified users (returns silently — we
     * don't want to leak which emails are unverified vs unknown).
     * Throws when the user is over the per-hour rate limit.
     */
    void resend(String email);
}
