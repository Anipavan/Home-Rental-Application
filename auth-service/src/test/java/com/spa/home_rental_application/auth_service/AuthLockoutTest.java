package com.spa.home_rental_application.auth_service;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import com.spa.home_rental_application.auth_service.Dto.Request.LoginRequest;
import com.spa.home_rental_application.auth_service.Entity.RefreshToken;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.EmailVerificationService;
import com.spa.home_rental_application.auth_service.Service.Impul.AuthServiceImpl;
import com.spa.home_rental_application.auth_service.Service.SystemSettingsService;
import com.spa.home_rental_application.auth_service.Service.external.PaymentServiceFeign;
import com.spa.home_rental_application.auth_service.Service.external.UserServiceFeign;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import com.spa.home_rental_application.auth_service.enums.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-15b: lockout-after-N-failed-logins coverage for AuthServiceImpl.
 *
 * <p>Verifies the security-critical behaviour that ships in
 * production via {@code app.auth.lockout.enabled=true} (off by
 * default for dev, but the prod profile flips it on):
 *
 *  POSITIVE
 *   - Successful login returns an AuthResponse with an access token
 *     and fires a SUCCESS audit event.
 *   - After a successful login, any leftover failedLoginAttempts +
 *     lockedUntil state is cleared.
 *   - After lockedUntil expires, a fresh attempt is no longer
 *     rejected by the pre-flight lockout check.
 *
 *  NEGATIVE
 *   - Wrong password throws BadCredentialsException AND publishes
 *     auth.login.failed on the audit channel.
 *   - The Nth failed attempt where N == MAX_FAILED_ATTEMPTS (5) sets
 *     lockedUntil on the user row.
 *   - A login attempt against a locked user throws LockedException
 *     EVEN WITH the correct password (we never even hit the
 *     AuthenticationManager — pre-flight check rejects).
 *   - Locked check skips entirely when lockout is disabled.
 *
 * Pure Mockito — no Spring context, no Feign, no real DB.
 */
@ExtendWith(MockitoExtension.class)
class AuthLockoutTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JWTUtil jwtUtil;
    @Mock JwtProperties jwtProperties;
    @Mock UserServiceFeign userServiceFeign;
    @Mock PaymentServiceFeign paymentServiceFeign;
    @Mock SystemSettingsService systemSettingsService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock AuthServiceEvents authEvents;
    @Mock AuditEventPublisher audit;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, authenticationManager, jwtUtil, jwtProperties,
                userServiceFeign, paymentServiceFeign, systemSettingsService,
                emailVerificationService,
                authEvents, audit,
                15L, BigDecimal.valueOf(999));
        // Lockout opt-in flag + thresholds are @Value-injected; populate
        // them manually since we're skipping Spring DI. Without
        // lockoutMinutes>0 the "lockedUntil > now" assertion races
        // the clock and flakes on fast machines.
        ReflectionTestUtils.setField(service, "lockoutEnabled", true);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15);

        // Generic stubbing that several tests share. lenient() because
        // not every test exercises every mock path.
        lenient().when(jwtProperties.getAccessTokenValiditySeconds()).thenReturn(900L);
        lenient().when(jwtProperties.getRefreshTokenValiditySeconds()).thenReturn(604_800L);
        lenient().when(jwtUtil.generateToken(any(), any())).thenReturn("access.jwt.token");
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private UserDetails freshUser(String userName) {
        UserDetails u = new UserDetails();
        u.setId(101L);
        u.setUserName(userName);
        u.setEmail(userName + "@example.com");
        u.setUserPassword("$2a$10$bcrypt-hash-placeholder");
        u.setUserRole(Roles.TENANT);
        u.setFailedLoginAttempts(0);
        u.setAccountNonLocked(true);
        return u;
    }

    private void stubSuccessfulAuthentication(UserDetails user) {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, "ignored", user.getAuthorities()));
    }

    /* ───────────────────────── POSITIVE ───────────────────────── */

    @Test
    @DisplayName("[+] successful login returns access token + emits SUCCESS audit event")
    void successful_login_returns_token_and_audits() {
        UserDetails user = freshUser("alice");
        when(userRepository.findByUserName(eq("alice"))).thenReturn(Optional.of(user));
        stubSuccessfulAuthentication(user);

        var resp = service.login(new LoginRequest("alice", "correct-pw"), "10.0.0.1", "ua");

        assertThat(resp.accessToken()).isEqualTo("access.jwt.token");
        assertThat(resp.userName()).isEqualTo("alice");

        verify(audit).publish(argThat(
                e -> "auth.login.success".equals(e.getEventType())
                        && "SUCCESS".equals(e.getOutcome())));
    }

    @Test
    @DisplayName("[+] successful login resets failed-attempt counter")
    void successful_login_clears_lockout_state() {
        UserDetails user = freshUser("bob");
        user.setFailedLoginAttempts(3);    // had 3 prior failures
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.HOURS));  // expired lock
        user.setAccountNonLocked(true);    // (matches expired-lock state)
        when(userRepository.findByUserName(eq("bob"))).thenReturn(Optional.of(user));
        stubSuccessfulAuthentication(user);

        service.login(new LoginRequest("bob", "correct-pw"), "ip", "ua");

        // success path saves the user with cleared counters
        ArgumentCaptor<UserDetails> savedCaptor = ArgumentCaptor.forClass(UserDetails.class);
        verify(userRepository).save(savedCaptor.capture());
        UserDetails after = savedCaptor.getValue();
        assertThat(after.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("[+] expired lockout window clears + lets a new attempt through")
    void expired_lock_clears_and_allows_retry() {
        UserDetails user = freshUser("carol");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));   // expired
        user.setAccountNonLocked(false);
        when(userRepository.findByUserName(eq("carol"))).thenReturn(Optional.of(user));
        stubSuccessfulAuthentication(user);

        var resp = service.login(new LoginRequest("carol", "correct-pw"), "ip", "ua");

        assertThat(resp.accessToken()).isNotBlank();
        // The pre-flight clearLockIfExpired bumped the user back to
        // unlocked — verify by checking userRepository.save was
        // called at least once. The post-success clear-counter path
        // ALSO saves when failedLoginAttempts > 0; since
        // clearLockIfExpired already zeroed the counter that second
        // save is a no-op-by-design, so we don't assert on it.
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(any(UserDetails.class));
        // Critical: pre-flight didn't throw, login flowed through
        // to authenticationManager.authenticate.
        verify(authenticationManager).authenticate(any());
    }

    /* ───────────────────────── NEGATIVE ───────────────────────── */

    @Test
    @DisplayName("[-] wrong password throws BadCredentials + emits FAILURE audit")
    void wrong_password_throws_and_audits() {
        UserDetails user = freshUser("dave");
        when(userRepository.findByUserName(eq("dave"))).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("nope"));

        assertThatThrownBy(() -> service.login(
                new LoginRequest("dave", "wrong"), "ip", "ua"))
                .isInstanceOf(BadCredentialsException.class);

        verify(audit).publishFailure(
                eq("auth.login.failed"),
                eq(null),
                eq("101"),
                anyString());
    }

    @Test
    @DisplayName("[-] N-th failed attempt (N == MAX) sets lockedUntil on the user row")
    void nth_failure_locks_account() {
        UserDetails user = freshUser("erin");
        user.setFailedLoginAttempts(4);   // already 4 fails — next failure is the 5th
        when(userRepository.findByUserName(eq("erin"))).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("nope"));

        assertThatThrownBy(() -> service.login(
                new LoginRequest("erin", "wrong"), "ip", "ua"))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<UserDetails> savedCaptor = ArgumentCaptor.forClass(UserDetails.class);
        verify(userRepository).save(savedCaptor.capture());
        UserDetails locked = savedCaptor.getValue();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
        assertThat(locked.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("[-] login on locked account throws LockedException — auth manager never invoked")
    void locked_account_rejects_at_preflight() {
        UserDetails user = freshUser("frank");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));   // still locked
        user.setAccountNonLocked(false);
        when(userRepository.findByUserName(eq("frank"))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(
                new LoginRequest("frank", "doesnt-matter"), "ip", "ua"))
                .isInstanceOf(LockedException.class)
                .hasMessageContaining("locked");

        // Critical assertion: we never even tried to authenticate.
        // That's what makes the pre-flight check defensive against
        // online password-guessing during the lockout window.
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("[-] lockout-disabled: locked user still gets to authenticate")
    void disabled_lockout_skips_preflight() {
        // Flip the master switch off
        ReflectionTestUtils.setField(service, "lockoutEnabled", false);

        UserDetails user = freshUser("greg");
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));   // would be locked
        user.setAccountNonLocked(false);
        // userRepository.findByUserName should NOT be called when lockout disabled
        stubSuccessfulAuthentication(user);

        var resp = service.login(new LoginRequest("greg", "any"), "ip", "ua");

        assertThat(resp.accessToken()).isNotBlank();
        verify(userRepository, never()).findByUserName(eq("greg"));
    }

    /* Mockito argument-matcher import — declared inline to keep the
       top of the file concise. */
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> m) {
        return org.mockito.ArgumentMatchers.argThat(m);
    }
}
