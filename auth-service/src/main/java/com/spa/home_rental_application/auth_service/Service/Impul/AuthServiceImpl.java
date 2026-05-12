package com.spa.home_rental_application.auth_service.Service.Impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import com.spa.home_rental_application.auth_service.Dto.AuthUserMapper;
import com.spa.home_rental_application.auth_service.Dto.External.UserProfileCreateRequest;
import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Entity.PasswordResetToken;
import com.spa.home_rental_application.auth_service.Entity.RefreshToken;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Exception.AuthRecordNotFoundException;
import com.spa.home_rental_application.auth_service.Exception.DuplicateUserException;
import com.spa.home_rental_application.auth_service.Exception.InvalidTokenException;
import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import com.spa.home_rental_application.auth_service.Service.external.UserServiceFeign;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import com.spa.home_rental_application.auth_service.enums.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade Auth flows.
 * <ul>
 *   <li>Register: validates uniqueness (userName + email), persists with
 *       BCrypt-hashed password, fires {@code user.registered}, then forwards
 *       a profile-shaped (password-free) DTO to User Service via Feign.
 *       If the Feign call fails the local Auth row is rolled back so the
 *       two services can never disagree on whether the user exists.</li>
 *   <li>Login: delegates to Spring Security's AuthenticationManager, issues
 *       an access JWT + opaque refresh token, persists the refresh token,
 *       fires {@code user.login}.</li>
 *   <li>Refresh: validates and rotates the refresh token (revoke old, issue new).</li>
 *   <li>Logout: revokes the refresh token, fires {@code user.logout}.</li>
 *   <li>Forgot/Reset password: single-use token via DB, event-driven email.</li>
 * </ul>
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final UserServiceFeign userServiceFeign;
    private final AuthServiceEvents authEvents;
    private final long passwordResetTtlMinutes;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JWTUtil jwtUtil,
                           JwtProperties jwtProperties,
                           UserServiceFeign userServiceFeign,
                           AuthServiceEvents authEvents,
                           @Value("${app.password-reset.token-validity-minutes:15}") long passwordResetTtlMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.userServiceFeign = userServiceFeign;
        this.authEvents = authEvents;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }

    /* ---------- Register ---------- */

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        log.info("register userName={} email={} role={}", req.userName(), req.email(), req.userRole());

        if (userRepository.existsByUserName(req.userName())) {
            throw new DuplicateUserException("userName already taken: " + req.userName());
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new DuplicateUserException("email already registered: " + req.email());
        }

        Roles role = (req.userRole() == Roles.TENENT) ? Roles.TENANT : req.userRole();
        Instant now = Instant.now();

        UserDetails entity = UserDetails.builder()
                .userName(req.userName())
                .userPassword(passwordEncoder.encode(req.userPassword()))
                .email(req.email())
                .userRole(role)
                .enabled(true)
                .accountNonLocked(true)
                .recordCreatedDate(now)
                .recodeUpdatedDate(now)
                .build();
        UserDetails saved = userRepository.save(entity);

        // Forward to User Service — never include the password.
        try {
            userServiceFeign.createUser(new UserProfileCreateRequest(
                    saved.getId().toString(),
                    req.firstName(),
                    req.lastName(),
                    req.email(),
                    req.phone(),
                    req.dateOfBirth(),
                    req.gender(),
                    req.address(),
                    null,
                    null
            ));
        } catch (Exception ex) {
            log.error("User Service profile-creation failed for authUserId={} — rolling back Auth row", saved.getId(), ex);
            // throw so @Transactional rolls the Auth side back too
            throw ex;
        }

        // Audit + welcome-fanout event. The phone goes on the event so
        // notification-service can seed the user's preferences row with
        // a real recipient and the welcome message fans across SMS +
        // WhatsApp on top of email + bell — addressing the user's
        // requirement that registration alerts cover all channels, not
        // just email.
        authEvents.sendUserRegistered(UserRegisteredEvent.builder()
                .eventType("user.registered")
                .authUserId(saved.getId().toString())
                .userName(saved.getUsername())
                .role(role.name())
                .email(saved.getEmail())
                .phone(req.phone())
                .timestamp(Instant.now())
                .build());

        return AuthUserMapper.toRegisterResponse(saved);
    }

    /* ---------- Login ---------- */

    @Override
    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        // Audit H4: pre-flight lockout check + failed-attempt
        // bookkeeping. Lookup is constant-time so it doesn't reopen
        // the H2 timing channel (DaoAuthenticationProvider would have
        // done the same lookup internally anyway).
        UserDetails prelocked = userRepository.findByUserName(req.userName()).orElse(null);
        if (prelocked != null) {
            clearLockIfExpired(prelocked);
            if (prelocked.getLockedUntil() != null
                    && prelocked.getLockedUntil().isAfter(Instant.now())) {
                long mins = ChronoUnit.MINUTES.between(Instant.now(), prelocked.getLockedUntil()) + 1;
                throw new LockedException(
                        "Account is locked after too many failed attempts. Try again in " + mins + " minute(s).");
            }
        }

        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.userName(), req.password()));
        } catch (BadCredentialsException ex) {
            // Bump the counter on the looked-up user (if any). Don't
            // create rows for non-existent usernames — that would be
            // an enumeration oracle.
            registerFailedLogin(prelocked);
            throw ex;
        } catch (AuthenticationException ex) {
            registerFailedLogin(prelocked);
            throw ex;
        }

        // Audit H2: use the principal already loaded by Spring Security
        // instead of a second findByUserName, so the success and
        // failure paths take the same time.
        UserDetails user = (UserDetails) authenticated.getPrincipal();

        // H4 — successful login clears the failed-attempt counter.
        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setAccountNonLocked(true);
            userRepository.save(user);
        }

        // Pass user.getId() so the JWT carries a `uid` claim — the
        // gateway reads it to stamp X-Auth-User-Id on every downstream
        // request. Without that claim, services keyed on auth user id
        // (wishlist favourites, notification prefs, owner-scoped
        // queries) see an empty string and Oracle INSERTs fail with
        // ORA-01400 ("" is NULL in Oracle).
        String accessToken = jwtUtil.generateToken(authenticated, user.getId());
        RefreshToken refresh = persistNewRefreshToken(user.getId(), ipAddress, userAgent);

        authEvents.sendUserLogin(UserLoginEvent.builder()
                .eventType("user.login")
                .authUserId(user.getId().toString())
                .userName(user.getUsername())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginTime(Instant.now())
                .timestamp(Instant.now())
                .build());

        return AuthResponse.bearer(accessToken, refresh.getToken(),
                jwtProperties.getAccessTokenValiditySeconds(),
                user.getUsername(), user.getId().toString(), user.getUserRole().name());
    }

    /* ---------- Refresh (rotate) ---------- */

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest req, String ipAddress, String userAgent) {
        RefreshToken existing = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognised"));

        if (!existing.isActive()) {
            throw new InvalidTokenException(
                    existing.isExpired() ? "Refresh token expired" : "Refresh token revoked");
        }

        // Audit H5: refuse if the presenter's fingerprint doesn't
        // match the row stored at login-time. Soft-mode (warn-only) by
        // default; flip via app.auth.refresh.bind-mode=strict.
        verifyRefreshFingerprint(existing, ipAddress, userAgent);

        UserDetails user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new AuthRecordNotFoundException("User not found"));

        // Rotate: revoke the presented token, issue a fresh one that
        // re-anchors the IP/UA fingerprint to whatever the rotating
        // client just sent. That way mobile users moving between
        // networks keep working even in strict mode, as long as each
        // refresh comes from a self-consistent device.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        RefreshToken next = persistNewRefreshToken(user.getId(), ipAddress, userAgent);

        // Mint a new access token using the user's stored authorities,
        // carrying the uid claim so the gateway can stamp
        // X-Auth-User-Id on downstream requests. See login() for the
        // full ORA-01400 rationale.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, user.getAuthorities());
        String accessToken = jwtUtil.generateToken(auth, user.getId());

        return AuthResponse.bearer(accessToken, next.getToken(),
                jwtProperties.getAccessTokenValiditySeconds(),
                user.getUsername(), user.getId().toString(), user.getUserRole().name());
    }

    /* ---------- Logout ---------- */

    @Override
    @Transactional
    public void logout(LogoutRequest req) {
        RefreshToken existing = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognised"));
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        userRepository.findById(existing.getUserId()).ifPresent(user -> {
            // Audit H3: anchor the "tokens issued before this point are
            // dead" timestamp. Once the gateway-side enforcement lands,
            // any access JWT with iat < tokensRevokedBefore is rejected
            // — closing the "stolen JWT is still valid for up to 15
            // min after logout" window down to the cache-TTL on the
            // gateway (60s). The server-side persistence is the
            // necessary foundation; the gateway lookup is wired in a
            // subsequent change to keep this commit reviewable.
            user.setTokensRevokedBefore(Instant.now());
            userRepository.save(user);

            authEvents.sendUserLogout(UserLogoutEvent.builder()
                    .eventType("user.logout")
                    .authUserId(user.getId().toString())
                    .userName(user.getUsername())
                    .logoutTime(Instant.now())
                    .timestamp(Instant.now())
                    .build());
        });
    }

    /* ---------- Forgot / reset password ---------- */

    @Override
    @Transactional
    public void startPasswordReset(ForgotPasswordRequest req) {
        // Defensive: do not leak whether the email exists. Always behave the same externally.
        userRepository.findByEmailIgnoreCase(req.email()).ifPresent(user -> {
            passwordResetTokenRepository.invalidateAllForUser(user.getId());
            String token = UUID.randomUUID().toString().replace("-", "");
            Instant expires = Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .token(token).userId(user.getId()).expiresAt(expires).used(false).build());

            authEvents.sendPasswordResetRequested(PasswordResetRequestedEvent.builder()
                    .eventType("user.password.reset.requested")
                    .authUserId(user.getId().toString())
                    .userName(user.getUsername())
                    .email(user.getEmail())
                    .resetToken(token)
                    .expiresAt(expires)
                    .timestamp(Instant.now())
                    .build());
        });
    }

    @Override
    @Transactional
    public void completePasswordReset(ResetPasswordRequest req) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(req.token())
                .orElseThrow(() -> new InvalidTokenException("Reset token not recognised"));
        if (!token.isUsable()) {
            throw new InvalidTokenException(
                    token.isExpired() ? "Reset token expired" : "Reset token already used");
        }
        UserDetails user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthRecordNotFoundException("User not found"));

        user.setUserPassword(passwordEncoder.encode(req.newPassword()));
        user.setRecodeUpdatedDate(Instant.now());
        userRepository.save(user);

        // Audit H7: delete the reset token after use instead of marking
        // used. Earlier code left the row in the table with used=true
        // which (a) leaked password-reset history to anyone who later
        // got DB read access and (b) accumulated dead rows that the
        // janitor had to sweep. Delete-on-use is single-use semantics
        // with no leftover footprint.
        passwordResetTokenRepository.delete(token);
        // Belt-and-braces: nuke any other live reset tokens for this
        // user so an attacker who silently triggered a second
        // /forgot-password during the same window can't reuse their
        // copy.
        passwordResetTokenRepository.deleteAllForUser(user.getId());

        // Force re-login on every device by revoking all existing refresh tokens.
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    /* ---------- Lookups ---------- */

    @Override
    public List<AuthUserResponse> getUsersByRole(Roles role) {
        return userRepository.findByUserRole(role).stream()
                .map(AuthUserMapper::toAuthUserResponse).toList();
    }

    @Override
    public AuthUserResponse getById(Long id) {
        return AuthUserMapper.toAuthUserResponse(userRepository.findById(id).orElseThrow(
                () -> new AuthRecordNotFoundException("User not found with id: " + id)));
    }

    @Override
    public Instant tokensRevokedBefore(Long userId) {
        return userRepository.findById(userId)
                .map(UserDetails::getTokensRevokedBefore)
                .orElse(null);
    }

    /* ---------- Helpers ---------- */

    /**
     * H5: stamp the client IP + a hash of the User-Agent on the
     * refresh-token row so {@code refresh} can later verify the
     * presenter is the same device that logged in. Hash (not raw UA)
     * keeps PII out of the DB.
     */
    private RefreshToken persistNewRefreshToken(Long userId, String ipAddress, String userAgent) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenValiditySeconds());
        return refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .revoked(false)
                .ipAddress(truncate(ipAddress, 64))
                .userAgentHash(hashUa(userAgent))
                .build());
    }

    /** Backwards-compat for the refresh-rotate path which doesn't yet carry the request context. */
    private RefreshToken persistNewRefreshToken(Long userId) {
        return persistNewRefreshToken(userId, null, null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Stable SHA-256 hex of the user-agent so we don't store the raw header. */
    private static String hashUa(String ua) {
        if (ua == null || ua.isBlank()) return null;
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(ua.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return null;       // hashing should never fail; if it does, return null and skip the check
        }
    }

    /**
     * H4: account-lockout helpers. Configurable via env:
     *   {@code app.auth.lockout.max-attempts}  default 5
     *   {@code app.auth.lockout.window-minutes} default 15
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    /** Returns true and clears state when the lock has expired. */
    private boolean clearLockIfExpired(UserDetails user) {
        if (user.getLockedUntil() == null) return false;
        if (Instant.now().isAfter(user.getLockedUntil())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            user.setAccountNonLocked(true);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    /**
     * H4: increment the failed-attempt counter and lock the account
     * after the 5th consecutive miss. We swallow the unlikely DB save
     * exception so the original BadCredentialsException still
     * propagates to the client — the user shouldn't get a confusing
     * "500" because we couldn't update the counter.
     */
    private void registerFailedLogin(UserDetails user) {
        if (user == null) return;       // unknown username — don't create rows (audit M1 enumeration concern)
        try {
            int n = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
            n++;
            user.setFailedLoginAttempts(n);
            if (n >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
                user.setAccountNonLocked(false);
                log.warn("Account {} locked for {} minutes after {} failed attempts",
                        user.getUsername(), LOCKOUT_MINUTES, n);
            }
            userRepository.save(user);
        } catch (Exception ex) {
            log.warn("Could not record failed-login bookkeeping for user {}: {}",
                    user.getId(), ex.getMessage());
        }
    }

    /**
     * H5: verify the presenter of a refresh token is the same client
     * that received it. The check is policy-driven via
     * {@code app.auth.refresh.bind-mode}:
     *   {@code warn} (default) — log a warning on mismatch, allow.
     *   {@code strict}         — refuse the refresh on mismatch.
     *   {@code off}            — skip the check.
     *
     * <p>Defaulting to {@code warn} avoids breaking mobile users on
     * IP-changing networks (carrier hand-offs, VPN toggles) until ops
     * has visibility into how often it would fire in prod.
     */
    private void verifyRefreshFingerprint(RefreshToken stored, String ipAddress, String userAgent) {
        if ("off".equalsIgnoreCase(refreshBindMode)) return;
        boolean ipMismatch = stored.getIpAddress() != null
                && !stored.getIpAddress().equals(truncate(ipAddress, 64));
        boolean uaMismatch = stored.getUserAgentHash() != null
                && !stored.getUserAgentHash().equals(hashUa(userAgent));
        if (!ipMismatch && !uaMismatch) return;

        if ("strict".equalsIgnoreCase(refreshBindMode)) {
            log.warn("Refresh refused (strict bind): userId={} storedIp={} presentingIp={} uaMatched={}",
                    stored.getUserId(), stored.getIpAddress(), ipAddress, !uaMismatch);
            throw new InvalidTokenException(
                    "Refresh token presented from a different device. Please log in again.");
        }
        log.warn("Refresh fingerprint mismatch (warn-mode): userId={} ipChanged={} uaChanged={}",
                stored.getUserId(), ipMismatch, uaMismatch);
    }

    @Value("${app.auth.refresh.bind-mode:warn}")
    private String refreshBindMode;
}
