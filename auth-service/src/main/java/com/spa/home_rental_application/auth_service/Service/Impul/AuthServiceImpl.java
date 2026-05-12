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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
        Authentication authenticated = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.userName(), req.password()));

        UserDetails user = userRepository.findByUserName(req.userName())
                .orElseThrow(() -> new AuthRecordNotFoundException("User not found: " + req.userName()));

        // Pass user.getId() so the JWT carries a `uid` claim — the
        // gateway reads it to stamp X-Auth-User-Id on every downstream
        // request. Without that claim, services keyed on auth user id
        // (wishlist favourites, notification prefs, owner-scoped
        // queries) see an empty string and Oracle INSERTs fail with
        // ORA-01400 ("" is NULL in Oracle).
        String accessToken = jwtUtil.generateToken(authenticated, user.getId());
        RefreshToken refresh = persistNewRefreshToken(user.getId());

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
    public AuthResponse refresh(RefreshTokenRequest req) {
        RefreshToken existing = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognised"));

        if (!existing.isActive()) {
            throw new InvalidTokenException(
                    existing.isExpired() ? "Refresh token expired" : "Refresh token revoked");
        }

        UserDetails user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new AuthRecordNotFoundException("User not found"));

        // Rotate: revoke the presented token, issue a fresh one.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        RefreshToken next = persistNewRefreshToken(user.getId());

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

        userRepository.findById(existing.getUserId()).ifPresent(user ->
                authEvents.sendUserLogout(UserLogoutEvent.builder()
                        .eventType("user.logout")
                        .authUserId(user.getId().toString())
                        .userName(user.getUsername())
                        .logoutTime(Instant.now())
                        .timestamp(Instant.now())
                        .build())
        );
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

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

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

    /* ---------- Helpers ---------- */

    private RefreshToken persistNewRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenValiditySeconds());
        return refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .revoked(false)
                .build());
    }
}
