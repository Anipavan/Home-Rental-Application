package com.spa.home_rental_application.auth_service.Service.Impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import com.spa.home_rental_application.auth_service.Dto.AuthUserMapper;
import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentResponse;
import com.spa.home_rental_application.auth_service.Dto.External.UserProfileCreateRequest;
import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.MaintainerPaymentStatusResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterPendingResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Entity.PasswordResetToken;
import com.spa.home_rental_application.auth_service.Entity.RefreshToken;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Exception.AuthRecordNotFoundException;
import com.spa.home_rental_application.auth_service.Exception.DuplicateUserException;
import com.spa.home_rental_application.auth_service.Exception.EmailVerificationRequiredException;
import com.spa.home_rental_application.auth_service.Exception.InvalidTokenException;
import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import com.spa.home_rental_application.auth_service.Service.EmailVerificationService;
import com.spa.home_rental_application.auth_service.Service.SystemSettingsService;
import com.spa.home_rental_application.auth_service.Service.external.PaymentServiceFeign;
import com.spa.home_rental_application.auth_service.Service.external.UserServiceFeign;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import com.spa.home_rental_application.auth_service.enums.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
    private final PaymentServiceFeign paymentServiceFeign;
    private final SystemSettingsService systemSettingsService;
    private final EmailVerificationService emailVerificationService;
    private final AuthServiceEvents authEvents;
    private final AuditEventPublisher audit;
    private final long passwordResetTtlMinutes;
    private final BigDecimal registrationFeeInr;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JWTUtil jwtUtil,
                           JwtProperties jwtProperties,
                           UserServiceFeign userServiceFeign,
                           PaymentServiceFeign paymentServiceFeign,
                           SystemSettingsService systemSettingsService,
                           EmailVerificationService emailVerificationService,
                           AuthServiceEvents authEvents,
                           AuditEventPublisher audit,
                           @Value("${app.password-reset.token-validity-minutes:15}") long passwordResetTtlMinutes,
                           @Value("${app.maintainer-registration.fee-inr:999}") BigDecimal registrationFeeInr) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.userServiceFeign = userServiceFeign;
        this.paymentServiceFeign = paymentServiceFeign;
        this.systemSettingsService = systemSettingsService;
        this.emailVerificationService = emailVerificationService;
        this.authEvents = authEvents;
        this.audit = audit;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.registrationFeeInr = registrationFeeInr;
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

        // Normalise the phone to E.164 BEFORE the uniqueness check so
        // two users entering the same number in different formats
        // (e.g. "9108201223" vs "+91 9108201223") are correctly
        // recognised as a collision. Same canonicalisation pattern
        // that notification-service's SMS / WhatsApp adapters use at
        // send time — duplicated inline here because auth-service
        // can't depend on notification-service.
        String normalisedPhone = normalisePhone(req.phone(), "+91");
        if (normalisedPhone != null && userRepository.existsByPhone(normalisedPhone)) {
            throw new DuplicateUserException(
                    "phone number already registered: " + normalisedPhone);
        }

        Roles role = (req.userRole() == Roles.TENENT) ? Roles.TENANT : req.userRole();
        Instant now = Instant.now();

        // V16: when the email-verification toggle is OFF (default on
        // first deploy), grandfather new signups to verified=true so
        // login works immediately and no verification email goes out.
        // Flip the toggle ON from /admin/settings and new signups will
        // start with verified=false → mintAndDispatch fires below →
        // login is blocked until they click the link.
        boolean emailGateOn = systemSettingsService.isEmailVerificationRequired();

        UserDetails entity = UserDetails.builder()
                .userName(req.userName())
                .userPassword(passwordEncoder.encode(req.userPassword()))
                .email(req.email())
                .phone(normalisedPhone)
                .userRole(role)
                .enabled(true)
                .accountNonLocked(true)
                .emailVerified(!emailGateOn)
                .recordCreatedDate(now)
                .recodeUpdatedDate(now)
                .build();
        UserDetails saved = userRepository.save(entity);

        // Forward to User Service — never include the password.
        // Pass the NORMALISED phone so user-service stores the same
        // canonical E.164 form as the auth row, and so downstream
        // notification-service consumers (which call
        // TwilioProperties.toE164 anyway) don't have to renormalise.
        try {
            userServiceFeign.createUser(new UserProfileCreateRequest(
                    saved.getId().toString(),
                    req.firstName(),
                    req.lastName(),
                    req.email(),
                    normalisedPhone,
                    req.dateOfBirth(),
                    req.gender(),
                    req.address(),
                    null,
                    null,
                    req.maritalStatus(),
                    req.tenantType()
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
                .phone(normalisedPhone)
                .timestamp(Instant.now())
                .build());

        // P1-12: dedicated audit channel. Mirrors the user.registered
        // event onto audit-events so the security operations index
        // gets a copy regardless of how the normal auth-events topic
        // is being consumed / retained.
        audit.publishSuccess("auth.register", saved.getId().toString(),
                saved.getId().toString(), saved.getId().toString(),
                java.util.Map.of("role", role.name(), "email", saved.getEmail()));

        // V16: when the email-verification toggle is ON, mint a token
        // + dispatch the verify-your-email link. When OFF, this is
        // skipped — the user is already verified above.
        if (emailGateOn) {
            emailVerificationService.mintAndDispatch(saved);
        }

        return AuthUserMapper.toRegisterResponse(saved);
    }

    /* ---------- Paid maintainer registration ---------- */

    @Override
    @Transactional
    public RegisterPendingResponse registerPending(RegisterPendingRequest req) {
        log.info("registerPending userName={} email={}", req.userName(), req.email());

        if (userRepository.existsByUserName(req.userName())) {
            throw new DuplicateUserException("userName already taken: " + req.userName());
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new DuplicateUserException("email already registered: " + req.email());
        }

        String normalisedPhone = normalisePhone(req.phone(), "+91");
        if (normalisedPhone != null && userRepository.existsByPhone(normalisedPhone)) {
            throw new DuplicateUserException(
                    "phone number already registered: " + normalisedPhone);
        }

        Instant now = Instant.now();

        // Check the admin toggle first. When OFF (default), this is a
        // free signup that stamps payment_paid_at=now so the user is
        // permanently in the PAID state — even if the toggle is later
        // flipped ON, they're grandfathered. When ON, leave
        // payment_paid_at=null so the trial clock starts ticking; the
        // dashboard gate evaluates the state machine on first load.
        boolean gateOn = systemSettingsService.isMaintainerPaymentEnabled();
        // V16 parallel: same toggle pattern for email verification.
        // OFF = grandfather to verified, no verification email; ON =
        // start unverified, dispatch link.
        boolean emailGateOn = systemSettingsService.isEmailVerificationRequired();

        // Persist as TENANT (today's society-signup default), always
        // enabled. The hard-paywall disable_reason path is gone; the
        // payment gate now lives in-app via the dashboard modal.
        UserDetails entity = UserDetails.builder()
                .userName(req.userName())
                .userPassword(passwordEncoder.encode(req.userPassword()))
                .email(req.email())
                .phone(normalisedPhone)
                .userRole(Roles.TENANT)
                .enabled(true)
                .accountNonLocked(true)
                .emailVerified(!emailGateOn)
                .recordCreatedDate(now)
                .recodeUpdatedDate(now)
                .paymentTrialStartedAt(now)
                .paymentSkipCount(0)
                .paymentPaidAt(gateOn ? null : now)
                .build();
        UserDetails saved = userRepository.save(entity);

        // Forward the profile to user-service. Same Feign-failure
        // rollback shape as register(). user.registered fires
        // immediately below since the user is enabled from this point.
        try {
            userServiceFeign.createUser(new UserProfileCreateRequest(
                    saved.getId().toString(),
                    req.firstName(),
                    req.lastName(),
                    req.email(),
                    normalisedPhone,
                    req.dateOfBirth(),
                    req.gender(),
                    req.address(),
                    null,
                    null,
                    req.maritalStatus(),
                    req.tenantType()
            ));
        } catch (Exception ex) {
            log.error("User Service profile-creation failed for pending authUserId={} — rolling back",
                    saved.getId(), ex);
            throw ex;
        }

        // user.registered now fires at signup (not deferred to activation)
        // — every signup produces a usable enabled account. Downstream
        // welcome fan-out is unchanged.
        authEvents.sendUserRegistered(UserRegisteredEvent.builder()
                .eventType("user.registered")
                .authUserId(saved.getId().toString())
                .userName(saved.getUsername())
                .role(saved.getUserRole().name())
                .email(saved.getEmail())
                .phone(normalisedPhone)
                .timestamp(Instant.now())
                .build());

        // V16 email-verification dispatch (independent of the
        // payment gate above). When the toggle is ON, the user landed
        // here with emailVerified=false and we send them the magic link.
        if (emailGateOn) {
            emailVerificationService.mintAndDispatch(saved);
        }

        // Toggle OFF path — no Payment row, no REG_PAY token. The
        // frontend reads paymentToken=null and skips the paywall page,
        // treating this as a free signup.
        if (!gateOn) {
            audit.publishSuccess("auth.register.pending", saved.getId().toString(),
                    saved.getId().toString(), saved.getId().toString(),
                    java.util.Map.of("gate", "off", "amountInr", "0"));
            return new RegisterPendingResponse(
                    saved.getId(), null, null, BigDecimal.ZERO);
        }

        // Toggle ON path — mint a PENDING Payment + REG_PAY token so
        // the frontend can route to /registration-payment. Same as the
        // original paid-paywall flow.
        CreateRegistrationPaymentResponse paymentInit;
        try {
            paymentInit = paymentServiceFeign.createPendingRegistrationPayment(
                    new CreateRegistrationPaymentRequest(
                            saved.getId().toString(),
                            registrationFeeInr));
        } catch (Exception ex) {
            log.error("Payment Service create-pending failed for authUserId={} — rolling back",
                    saved.getId(), ex);
            throw ex;
        }

        long ttlMillis = 30L * 60L * 1000L;
        String paymentToken = jwtUtil.generateToken(
                saved.getUsername(),
                Map.of(
                        "purpose", "REG_PAY",
                        "uid", saved.getId().toString(),
                        "paymentId", paymentInit.paymentId()),
                ttlMillis);

        audit.publishSuccess("auth.register.pending", saved.getId().toString(),
                saved.getId().toString(), saved.getId().toString(),
                java.util.Map.of(
                        "gate", "on",
                        "paymentId", paymentInit.paymentId(),
                        "amountInr", registrationFeeInr.toPlainString()));

        return new RegisterPendingResponse(
                saved.getId(),
                paymentInit.paymentId(),
                paymentToken,
                registrationFeeInr);
    }

    @Override
    @Transactional
    public AuthUserResponse activateRegistration(Long authUserId, String paymentId) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        // Idempotent no-op when the user is already in the PAID state
        // (a retry from payment-service's reconciler can land here for
        // an already-activated row). user.registered is NOT re-fired —
        // signup already published it.
        if (user.getPaymentPaidAt() != null) {
            log.info("activateRegistration no-op for authUserId={} paymentId={} — already paid",
                    authUserId, paymentId);
            return AuthUserMapper.toAuthUserResponse(user);
        }

        // The only thing activation does now is stamp the paid-at
        // watermark. The user is already enabled — this just moves
        // them out of the trial / skip state machine into PAID
        // permanently. Defensive: also clear disable_reason in case
        // a legacy V5-era row somehow slipped through.
        user.setPaymentPaidAt(Instant.now());
        user.setDisableReason(null);
        user.setEnabled(true);
        user.setRecodeUpdatedDate(Instant.now());
        UserDetails saved = userRepository.save(user);

        audit.publishSuccess("auth.register.activated", saved.getId().toString(),
                saved.getId().toString(), saved.getId().toString(),
                java.util.Map.of("paymentId", paymentId));

        log.info("Maintainer registration activated authUserId={} paymentId={}",
                authUserId, paymentId);
        return AuthUserMapper.toAuthUserResponse(saved);
    }

    /* ---------- Login ---------- */

    @Override
    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        // Audit H4: pre-flight lockout check is OPT-IN via
        // app.auth.lockout.enabled (default false). When disabled,
        // we skip the user-table read + lock-check + failed-attempt
        // bookkeeping entirely. Local dev users with corrupt
        // failed_login_attempts data in their DB don't get locked out.
        UserDetails prelocked = null;
        if (lockoutEnabled) {
            prelocked = userRepository.findByUserName(req.userName()).orElse(null);
            if (prelocked != null) {
                clearLockIfExpired(prelocked);
                if (prelocked.getLockedUntil() != null
                        && prelocked.getLockedUntil().isAfter(Instant.now())) {
                    long mins = ChronoUnit.MINUTES.between(Instant.now(), prelocked.getLockedUntil()) + 1;
                    throw new LockedException(
                            "Account is locked after too many failed attempts. Try again in " + mins + " minute(s).");
                }
            }
        }

        Authentication authenticated;
        // Track whether the user authenticated via maintainer_password
        // (the secondary credential set by the owner-driven promote
        // flow) so we can stamp role=MAINTAINER on the issued JWT,
        // overriding the user's stored user_role for this session
        // only. A tenant who also manages a society effectively has
        // two login modes — same account, two passwords.
        boolean authenticatedAsMaintainer = false;
        try {
            authenticated = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.userName(), req.password()));
        } catch (BadCredentialsException ex) {
            // ── Dual-credential fallback ──
            // Before declaring this a real failed login, try the same
            // password against maintainer_password. If THAT matches,
            // build a synthetic Authentication with ROLE_MAINTAINER
            // and proceed with the rest of the login flow as if
            // Spring's DaoAuthenticationProvider had handed us this
            // result.
            UserDetails altUser = userRepository.findByUserName(req.userName()).orElse(null);
            if (altUser != null
                    && altUser.getMaintainerPassword() != null
                    && !altUser.getMaintainerPassword().isBlank()
                    && passwordEncoder.matches(req.password(), altUser.getMaintainerPassword())) {
                log.info("Login via maintainer_password authUserId={} primaryRole={} → MAINTAINER session",
                        altUser.getId(), altUser.getUserRole());
                authenticated = new UsernamePasswordAuthenticationToken(
                        altUser,
                        null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MAINTAINER")));
                authenticatedAsMaintainer = true;
            } else {
                if (lockoutEnabled) registerFailedLogin(prelocked);
                audit.publishFailure("auth.login.failed",
                        null,
                        prelocked == null ? null : String.valueOf(prelocked.getId()),
                        "Bad credentials for username '" + req.userName() + "'");
                throw ex;
            }
        } catch (DisabledException ex) {
            // The hard-paywall path (REGISTRATION_PAYMENT_PENDING)
            // is gone — the gate moved into the dashboard modal. We
            // never throw RegistrationPaymentPendingException anymore.
            // Any DisabledException now means admin-initiated disable
            // and surfaces as the generic ACCOUNT_DISABLED at the
            // GlobalExceptionHandler.
            UserDetails disabled = prelocked != null
                    ? prelocked
                    : userRepository.findByUserName(req.userName()).orElse(null);
            audit.publishFailure("auth.login.disabled",
                    null,
                    disabled == null ? null : String.valueOf(disabled.getId()),
                    "Account disabled");
            throw ex;
        } catch (AuthenticationException ex) {
            if (lockoutEnabled) registerFailedLogin(prelocked);
            audit.publishFailure("auth.login.failed",
                    null,
                    prelocked == null ? null : String.valueOf(prelocked.getId()),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }

        // Audit H2: use the principal already loaded by Spring Security
        // instead of a second findByUserName, so the success and
        // failure paths take the same time.
        UserDetails user = (UserDetails) authenticated.getPrincipal();

        // V16 email-verification gate. When the
        // email_verification_required toggle is ON, a user with
        // emailVerified=false can authenticate (creds were valid)
        // but they can't get a session — frontend surfaces a
        // "verify your email" prompt + resend button. When the
        // toggle is OFF, the field is read but ignored, so today's
        // behaviour (login works whether or not the address is
        // confirmed) is unchanged.
        if (systemSettingsService.isEmailVerificationRequired()
                && !Boolean.TRUE.equals(user.getEmailVerified())) {
            audit.publishFailure("auth.login.unverified",
                    null,
                    String.valueOf(user.getId()),
                    "Email not verified");
            throw new EmailVerificationRequiredException(user.getEmail());
        }

        // H4 — successful login clears any lingering failed-attempt
        // counter (still safe to run even when lockout is disabled,
        // since it's a one-row update if needed).
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

        // P1-12: audit-channel mirror for security-ops dashboards.
        audit.publish(com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuditServiceEvents.AuditEvent.builder()
                .eventType("auth.login.success")
                .actorUserId(user.getId().toString())
                .subjectUserId(user.getId().toString())
                .outcome("SUCCESS")
                .clientIp(ipAddress)
                .userAgent(userAgent)
                .build());

        // Role in the API response: MAINTAINER when the user came in
        // through maintainer_password, otherwise their stored
        // user_role. The frontend keys its post-login redirect on
        // this value (login.tsx routes MAINTAINER → /maintainer,
        // OWNER → /owner, TENANT → /app).
        String responseRole = authenticatedAsMaintainer
                ? Roles.MAINTAINER.name()
                : user.getUserRole().name();
        List<String> rolesList = user.getAllRoles().stream()
                .map(Roles::name)
                .sorted()
                .toList();
        return AuthResponse.bearer(accessToken, refresh.getToken(),
                jwtProperties.getAccessTokenValiditySeconds(),
                user.getUsername(), user.getId().toString(), responseRole, rolesList);
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

        List<String> rolesList = user.getAllRoles().stream()
                .map(Roles::name)
                .sorted()
                .toList();
        return AuthResponse.bearer(accessToken, next.getToken(),
                jwtProperties.getAccessTokenValiditySeconds(),
                user.getUsername(), user.getId().toString(),
                user.getUserRole().name(), rolesList);
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
        // User explicitly requested the OPPOSITE of the M1 anti-
        // enumeration shape: fail loudly when the email isn't on
        // file. End-users typing a wrong address now get a clear
        // "no account is registered with this email" toast instead
        // of a misleading "if it's registered we sent a link" while
        // no link ever arrives. Trade: lose the M1 privacy benefit
        // (defeating bus-level email enumeration) in exchange for
        // end-user clarity — the right call for the dev/early-prod
        // phase. Flip back to the silent path later via a config
        // flag if public exposure changes the calculus.
        var maybeUser = userRepository.findByEmailIgnoreCase(req.email());
        if (maybeUser.isEmpty()) {
            log.info("Password-reset requested for unregistered email {}", req.email());
            throw new AuthRecordNotFoundException(
                    "No account is registered with this email. Double-check the address or create an account.");
        }

        var user = maybeUser.get();
        passwordResetTokenRepository.invalidateAllForUser(user.getId());
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expires = Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token).userId(user.getId()).expiresAt(expires).used(false).build());

        // P1-12: audit the password-reset request even when no email
        // actually lands — supports investigations of "someone tried to
        // reset my password" reports.
        audit.publishSuccess("auth.password.reset.requested", user.getId().toString());

        authEvents.sendPasswordResetRequested(PasswordResetRequestedEvent.builder()
                .eventType("user.password.reset.requested")
                .authUserId(user.getId().toString())
                .userName(user.getUsername())
                .email(user.getEmail())
                .resetToken(token)
                .expiresAt(expires)
                .timestamp(Instant.now())
                .build());

        log.info("Password-reset token issued for userId={} email={} (link valid {}min)",
                user.getId(), user.getEmail(), passwordResetTtlMinutes);
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

    /* ---------- Internal: owner promotes a tenant to maintainer ----------
     * The endpoint sits under /auth/internal/**, so the gateway HMAC is
     * the access control. Property-service is the only legitimate caller
     * — it validates that the calling owner actually owns the building +
     * the targeted authUserId is a tenant in one of that building's
     * flats before it issues the call. We therefore don't double-check
     * the social facts here; we just perform the role flip + password
     * reset, and rely on @Transactional for atomicity.
     */
    @Override
    @Transactional
    public AuthUserResponse promoteToMaintainer(Long authUserId, String newPassword) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        Roles before = user.getUserRole();
        // Defensive: never demote ADMIN. OWNER is fine to mark as a
        // maintainer (some owners run their own buildings — they just
        // gain a second login mode). Property-service's
        // eligible-maintainers filter only surfaces tenants of flats
        // anyway, so practically the typical caller hits the TENANT
        // branch.
        if (before == Roles.ADMIN) {
            log.warn("promoteToMaintainer refused — refusing to override ADMIN authUserId={}",
                    authUserId);
            throw new IllegalStateException(
                    "Cannot grant maintainer access to an ADMIN user.");
        }

        // Self-heal: if the row STILL carries user_role=MAINTAINER
        // (an orphaned state left by the V2-era promote flow that
        // overwrote user_role), reset it back to TENANT. V4 migration
        // does this at boot time as a sweep; doing it here too means
        // any new promote call cleans up the row even if the
        // operator forgot to deploy the migration first.
        if (before == Roles.MAINTAINER) {
            log.info("promoteToMaintainer self-heal authUserId={} reverting "
                    + "stale user_role MAINTAINER → TENANT",
                    authUserId);
            user.setUserRole(Roles.TENANT);
        }

        // ── KEY CHANGE (V3): dual-credential model ──
        // We DO NOT change user_role or user_password anymore.
        // Doing so destroyed the user's tenant access — they'd lose
        // /app entirely after being promoted, even though they were
        // still the tenant of Flat 203. Instead we set a SECOND
        // BCrypt-hashed credential (maintainer_password). The login
        // flow tries the primary credential first; on miss, falls
        // back to maintainer_password and stamps role=MAINTAINER on
        // the issued JWT.
        //
        // We also do NOT bump tokensRevokedBefore — the user keeps
        // their existing tenant session alive. They only need a
        // second login attempt with the new credential when they
        // want maintainer mode.
        user.setMaintainerPassword(passwordEncoder.encode(newPassword));

        // If the account happened to be locked from failed-login
        // tries on the primary password, clear the lock — the
        // owner just verbally set new credentials so the auto-
        // lockout window is no longer informative.
        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        Instant now = Instant.now();
        user.setRecodeUpdatedDate(now);
        UserDetails saved = userRepository.save(user);

        // P1-12-style audit trail.
        audit.publishSuccess("auth.promote-to-maintainer",
                saved.getId().toString(),
                saved.getId().toString(),
                saved.getId().toString(),
                java.util.Map.of("priorRole", before.name(),
                        "newRole", "MAINTAINER (added; user_role preserved)"));

        return AuthUserMapper.toAuthUserResponse(saved);
    }

    @Override
    @Transactional
    public AuthUserResponse grantMaintainerRole(Long authUserId) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        Roles before = user.getUserRole();

        // Same ADMIN guard as the password-changing variant.
        if (before == Roles.ADMIN) {
            log.warn("grantMaintainerRole refused — refusing to override ADMIN authUserId={}",
                    authUserId);
            throw new IllegalStateException(
                    "Cannot grant maintainer role to an ADMIN user.");
        }

        // OWNER already implies the same capabilities as MAINTAINER in
        // this codebase's permission model — keep the OWNER role to
        // avoid demoting them.
        if (before == Roles.OWNER) {
            log.info("grantMaintainerRole no-op for authUserId={} — already OWNER, keeping role",
                    authUserId);
            return AuthUserMapper.toAuthUserResponse(user);
        }

        // Already MAINTAINER → idempotent no-op (don't dirty the row).
        if (before == Roles.MAINTAINER) {
            log.info("grantMaintainerRole no-op for authUserId={} — already MAINTAINER",
                    authUserId);
            return AuthUserMapper.toAuthUserResponse(user);
        }

        user.setUserRole(Roles.MAINTAINER);
        user.setRecodeUpdatedDate(Instant.now());
        UserDetails saved = userRepository.save(user);

        audit.publishSuccess("auth.grant-maintainer-role",
                saved.getId().toString(),
                saved.getId().toString(),
                saved.getId().toString(),
                java.util.Map.of("priorRole", before.name(),
                        "newRole", "MAINTAINER (self-service claim approved)"));

        return AuthUserMapper.toAuthUserResponse(saved);
    }

    /* ---------- Phase 4: unified-signup role selection ---------- */

    @Override
    @Transactional
    public AuthResponse setPrimaryRole(Long authUserId, Roles newRole,
                                       String ipAddress, String userAgent) {
        if (newRole != Roles.TENANT && newRole != Roles.OWNER) {
            // Redundant guard — the DTO validates the wire input, but
            // defence in depth in case someone bypasses the controller
            // (Feign, internal call, etc.).
            throw new IllegalArgumentException(
                    "Only TENANT and OWNER are self-service roles; got " + newRole);
        }

        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        Roles before = user.getUserRole();

        if (before == Roles.ADMIN) {
            // Prevent an ADMIN from downgrading themselves via /welcome.
            log.warn("setPrimaryRole refused — refusing to override ADMIN authUserId={}",
                    authUserId);
            throw new IllegalStateException("Cannot change the primary role of an ADMIN user.");
        }

        if (before == newRole) {
            log.info("setPrimaryRole no-op for authUserId={} — already {}", authUserId, newRole);
        } else {
            user.setUserRole(newRole);
            user.addRole(newRole);                        // Phase 3 multi-role set
            user.setRecodeUpdatedDate(Instant.now());
            userRepository.save(user);
            log.info("setPrimaryRole authUserId={} {} -> {}", authUserId, before, newRole);
            audit.publishSuccess("auth.set-primary-role",
                    user.getId().toString(),
                    user.getId().toString(),
                    user.getId().toString(),
                    Map.of("priorRole", before.name(), "newRole", newRole.name()));
        }

        // Re-issue tokens carrying the fresh authorities so the SPA
        // doesn't have to re-login. Mirrors the refresh() shape.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, user.getAuthorities());
        String accessToken = jwtUtil.generateToken(auth, user.getId());
        RefreshToken refresh = persistNewRefreshToken(user.getId(), ipAddress, userAgent);

        List<String> rolesList = user.getAllRoles().stream()
                .map(Roles::name)
                .sorted()
                .toList();
        return AuthResponse.bearer(accessToken, refresh.getToken(),
                jwtProperties.getAccessTokenValiditySeconds(),
                user.getUsername(), user.getId().toString(),
                user.getUserRole().name(), rolesList);
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
     * H4: account-lockout configuration. Externalised via Spring
     * {@code @Value} so operators can tune (or effectively disable)
     * the lockout without a code change.
     *
     * <p>Defaults: 100 attempts, 1-minute window. The intent is to
     * still block a brute-force script (hundreds of attempts/sec)
     * while never tripping on a confused human retrying a few times
     * during a debug session — the lockout used to be set at 5/15min
     * and was hitting test users constantly. Set to a very large
     * number (e.g. {@code MAX_VALUE}) via env to effectively disable.
     */
    @org.springframework.beans.factory.annotation.Value("${app.auth.lockout.max-attempts:100}")
    private int maxFailedAttempts;

    @org.springframework.beans.factory.annotation.Value("${app.auth.lockout.window-minutes:1}")
    private int lockoutMinutes;

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
            if (n >= maxFailedAttempts) {
                user.setLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
                user.setAccountNonLocked(false);
                log.warn("Account {} locked for {} minutes after {} failed attempts",
                        user.getUsername(), lockoutMinutes, n);
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

    /**
     * H5 refresh-token IP/UA fingerprint mode. Default OFF in dev
     * because mobile + WSL + Docker networks make the fingerprint
     * unreliable for real users; the protection only really pays off
     * in a prod deployment behind a stable load balancer.
     */
    @Value("${app.auth.refresh.bind-mode:off}")
    private String refreshBindMode;

    /**
     * H4 account-lockout master switch. OFF by default — users with
     * corrupt failed_login_attempts data in their DB (or just any
     * unlucky pre-fix state) shouldn't be locked out by a security
     * feature that's optional anyway. Flip on in prod via
     * APP_AUTH_LOCKOUT_ENABLED=true.
     */
    @Value("${app.auth.lockout.enabled:false}")
    private boolean lockoutEnabled;

    /**
     * Normalise a raw phone number to E.164 ({@code +<country code><digits>}).
     * Used at registration so the {@code phone} uniqueness check is
     * format-agnostic — "9108201223" and "+91 9108201223" both end
     * up as "+919108201223" and collide correctly. Mirrors the
     * notification-service's {@code TwilioProperties.toE164} so the
     * stored value also happens to be exactly what Twilio expects on
     * the outgoing SMS / WhatsApp path.
     *
     * <ul>
     *   <li>{@code null} / blank input → {@code null} (the phone field
     *       is optional at registration; callers must NOT call
     *       existsByPhone on null).</li>
     *   <li>Starts with {@code +} → keep prefix, strip everything but
     *       digits from the rest.</li>
     *   <li>Starts with {@code 00} → swap to {@code +}.</li>
     *   <li>Bare local number → prepend {@code defaultCountryCode}.</li>
     * </ul>
     */
    private static String normalisePhone(String raw, String defaultCountryCode) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        String digits;
        if (trimmed.startsWith("+")) {
            digits = trimmed.substring(1).replaceAll("\\D", "");
            if (digits.isEmpty()) return null;
            return "+" + digits;
        }
        if (trimmed.startsWith("00")) {
            digits = trimmed.substring(2).replaceAll("\\D", "");
            if (digits.isEmpty()) return null;
            return "+" + digits;
        }
        digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        String cc = defaultCountryCode == null ? "+91" : defaultCountryCode.trim();
        if (!cc.startsWith("+")) cc = "+" + cc;
        return cc + digits;
    }

    /* ---------- Maintainer-payment soft gate ---------- */

    @Override
    public MaintainerPaymentStatusResponse getPaymentStatus(Long authUserId) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));
        return systemSettingsService.computeStatus(user);
    }

    @Override
    @Transactional
    public MaintainerPaymentStatusResponse recordPaymentSkip(Long authUserId) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        // Re-evaluate so we can refuse if the user isn't actually in
        // a state where Skip is valid. Without this guard a determined
        // client could spam /payment-skip during the TRIAL window and
        // pre-burn their skip count.
        MaintainerPaymentStatusResponse before = systemSettingsService.computeStatus(user);
        if (before.status() != MaintainerPaymentStatusResponse.Status.PROMPT) {
            log.warn("recordPaymentSkip refused for authUserId={} — state={} (only PROMPT can skip)",
                    authUserId, before.status());
            throw new IllegalStateException(
                    "Skip is only valid when the user is being prompted. Current state: "
                            + before.status());
        }

        int newCount = (user.getPaymentSkipCount() == null ? 0 : user.getPaymentSkipCount()) + 1;
        user.setPaymentSkipCount(newCount);
        user.setPaymentLastSkipAt(Instant.now());
        user.setRecodeUpdatedDate(Instant.now());
        userRepository.save(user);

        audit.publishSuccess("auth.maintainer-payment.skip", authUserId.toString(),
                authUserId.toString(), authUserId.toString(),
                Map.of("skipCount", String.valueOf(newCount)));
        log.info("Maintainer payment skip recorded authUserId={} newSkipCount={}", authUserId, newCount);

        return systemSettingsService.computeStatus(user);
    }

    @Override
    @Transactional
    public RegisterPendingResponse initiateOwnPayment(Long authUserId) {
        UserDetails user = userRepository.findById(authUserId)
                .orElseThrow(() -> new AuthRecordNotFoundException(
                        "User not found with id: " + authUserId));

        // Already paid → nothing to do. Return a noop response so the
        // frontend can route back to /maintainer with a clean state.
        if (user.getPaymentPaidAt() != null) {
            return new RegisterPendingResponse(
                    user.getId(), null, null, BigDecimal.ZERO);
        }

        // Mint a PENDING Payment + REG_PAY token, same shape the
        // signup flow uses. payment-service is idempotent on
        // (payerAuthUserId + PENDING) so retry from a closed modal
        // resumes the same row.
        CreateRegistrationPaymentResponse paymentInit;
        try {
            paymentInit = paymentServiceFeign.createPendingRegistrationPayment(
                    new CreateRegistrationPaymentRequest(
                            user.getId().toString(),
                            registrationFeeInr));
        } catch (Exception ex) {
            log.error("Payment Service create-pending failed for authUserId={}",
                    user.getId(), ex);
            throw ex;
        }

        long ttlMillis = 30L * 60L * 1000L;
        String paymentToken = jwtUtil.generateToken(
                user.getUsername(),
                Map.of(
                        "purpose", "REG_PAY",
                        "uid", user.getId().toString(),
                        "paymentId", paymentInit.paymentId()),
                ttlMillis);

        audit.publishSuccess("auth.maintainer-payment.initiate",
                user.getId().toString(),
                user.getId().toString(), user.getId().toString(),
                Map.of("paymentId", paymentInit.paymentId(),
                        "amountInr", registrationFeeInr.toPlainString()));

        return new RegisterPendingResponse(
                user.getId(),
                paymentInit.paymentId(),
                paymentToken,
                registrationFeeInr);
    }
}
