package com.spa.home_rental_application.auth_service.Entity;

import com.spa.home_rental_application.auth_service.enums.Roles;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auth-side user record. Holds credentials and role; profile data lives in
 * User Service. The {@code @Transient} fields are convenience holders used
 * during registration so the same record can be projected onto the
 * downstream user-profile DTO without leaking persistence semantics.
 */
@Entity
@Table(name = "user_details_table", indexes = {
        @Index(name = "idx_user_details_username", columnList = "user_name", unique = true),
        @Index(name = "idx_user_details_email", columnList = "email", unique = true),
        // Phone numbers are unique like email/userName so a user
        // can't register a second account with the same mobile
        // number. Index is unique + non-null-tolerant: legacy rows
        // with phone=NULL don't violate the constraint because
        // Oracle's unique index treats multiple NULLs as distinct.
        @Index(name = "idx_user_details_phone", columnList = "phone", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDetails implements org.springframework.security.core.userdetails.UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false, unique = true, length = 100)
    private String userName;

    @Column(name = "user_password", nullable = false)
    private String userPassword;

    /**
     * Separate BCrypt-hashed credential the user enters to log in as
     * MAINTAINER instead of their primary role. Set by the owner-
     * initiated {@code promote-to-maintainer} flow. Null for users
     * who aren't a maintainer of any building.
     *
     * <p>Login flow: AuthService.login tries {@link #userPassword}
     * first via standard Spring DaoAuthenticationProvider. On
     * BadCredentialsException, it falls back to a manual BCrypt
     * compare against this column. If THAT matches, the issued
     * JWT carries {@code role=MAINTAINER} for that session — the
     * stored {@link #userRole} is preserved (so the tenant /
     * owner identity isn't lost).
     */
    @Column(name = "maintainer_password")
    private String maintainerPassword;

    @Column(name = "email", unique = true, length = 200)
    private String email;

    /**
     * Normalised phone number (E.164: {@code +<country code><digits>}).
     * Persisted with a unique constraint so a user can't register a
     * second account with the same mobile number. Nullable for
     * backwards-compat with rows created before this column existed —
     * Oracle's unique index allows multiple NULLs.
     *
     * <p>AuthServiceImpl.register normalises the raw user input to
     * E.164 before persisting (defaulting to +91 when the user types
     * a bare 10-digit Indian number), so the uniqueness check works
     * even if two users format their input differently (e.g.
     * "9108201223" vs "+91 9108201223" both end up as
     * "+919108201223" and collide correctly).
     */
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    /**
     * The "primary role" — the one a user signed up with and the one
     * the frontend's post-login redirect keys on. Kept as a top-level
     * column (rather than synthesised from {@link #userRoles}) so the
     * gateway's role-header stamping, every existing {@code
     * findByUserRole} query, and the legacy {@code AuthUserResponse}
     * shape all continue to work without changes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 32)
    private Roles userRole;

    /**
     * V17 — multi-role foundation. The set of every role this user
     * holds, including their primary role. Backed by the
     * {@code user_roles} join table (V17 migration creates it +
     * backfills one row per user from the legacy {@link #userRole}
     * column).
     *
     * <p>EAGER fetch: {@link #getAuthorities()} runs on every JWT
     * issuance + every gateway-stamped request, so a lazy proxy
     * would either crash outside the transaction or hammer the DB
     * once per call. The set never exceeds the number of declared
     * Roles (today: 4), so eager loading is free.
     */
    @ElementCollection(targetClass = Roles.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Roles> userRoles = new HashSet<>();

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * V16: has the user clicked the magic-link in their post-signup
     * verification email? Defaults to false for new signups; existing
     * users are grandfathered to true via the V16 migration backfill.
     *
     * <p>Login enforcement is gated on the
     * {@code email_verification_required} {@link SystemSetting}: when
     * that toggle is OFF (the default on first deploy) this column is
     * read but ignored, so an unverified user can still log in. Flip
     * the toggle ON from {@code /admin/settings} and AuthServiceImpl.login
     * will start rejecting unverified accounts with
     * {@code errorCode=EMAIL_VERIFICATION_REQUIRED}.
     */
    @Column(name = "email_verified", nullable = false,
            columnDefinition = "NUMBER(1) DEFAULT 0")
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * Why the account is currently disabled, when {@link #enabled} is
     * false. Null on enabled accounts and on accounts disabled for
     * generic reasons (admin action, etc.).
     *
     * <p>Today the only populated value is
     * {@code REGISTRATION_PAYMENT_PENDING}: a new maintainer signup
     * has been created but the ₹999 activation fee hasn't cleared
     * yet. AuthServiceImpl.login() reads this column when it catches
     * DisabledException so it can surface a distinct error code to
     * the frontend — the paywall path, not the generic
     * ACCOUNT_DISABLED path.
     *
     * <p>Cleared back to NULL by activateRegistration() once the
     * Razorpay callback marks the payment PAID.
     */
    @Column(name = "disable_reason", length = 60)
    private String disableReason;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    /**
     * Audit H4: counter for consecutive failed logins. Reset to zero
     * after a successful login or after the lock expires. We use it
     * to gate the lock-out check in {@code AuthServiceImpl.login}
     * without persisting a separate audit row per attempt.
     *
     * <p>Column-definition includes an Oracle DEFAULT 0 so that
     * Hibernate's {@code ddl-auto=update} can ADD this column to an
     * already-populated {@code user_details_table} without hitting
     * the ORA-01758 trap that crashed the service when we added
     * the {@code is_cover} column to PropertyImage.
     */
    @Column(name = "failed_login_attempts", nullable = false,
            columnDefinition = "NUMBER(10) DEFAULT 0 ")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * Audit H4: timestamp this account is locked until. Null = not
     * locked. The lockout helper sets this to now()+15 minutes after
     * the 5th consecutive failure; a successful login (or 15-minute
     * timeout) clears it.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Audit H3: monotonic instant — every JWT issued before this point
     * is considered revoked. {@code logout} bumps it to now() so the
     * access tokens still in tenants' hands are invalidated immediately.
     * The gateway enforces this via {@code iat &gt;= tokensRevokedBefore}
     * on every request (cached for 60s).
     */
    @Column(name = "tokens_revoked_before")
    private Instant tokensRevokedBefore;

    /* ---------- Maintainer-payment soft gate (V15) ---------- */

    /**
     * When the 30-day free trial clock started. For maintainer
     * signups this is the registration timestamp; the
     * {@link com.spa.home_rental_application.auth_service.scheduler.MaintainerPaymentStatusEvaluator}
     * uses {@code trialStartedAt + 30 days} as the trial-expiry
     * boundary. Grandfathered users get
     * {@code recordCreatedDate} stamped here on the V15 migration so
     * the column never holds NULL.
     */
    @Column(name = "payment_trial_started_at")
    private Instant paymentTrialStartedAt;

    /**
     * How many times this user has clicked "Skip for 4 more days"
     * on the post-trial payment modal. 0, 1, or 2. The third prompt
     * is mandatory — see {@code MaintainerPaymentService.computeStatus}
     * for the full state machine.
     */
    @Column(name = "payment_skip_count", nullable = false,
            columnDefinition = "NUMBER(2) DEFAULT 0")
    @Builder.Default
    private Integer paymentSkipCount = 0;

    /**
     * Timestamp of the most recent Skip click. With
     * {@link #paymentSkipCount} this lets the state machine compute
     * the 4-day grace window between prompts.
     */
    @Column(name = "payment_last_skip_at")
    private Instant paymentLastSkipAt;

    /**
     * When the activation fee was paid (or stamped for grandfathered
     * users on the V15 migration). Non-null = user is permanently in
     * the PAID state and the gate never fires for them.
     */
    @Column(name = "payment_paid_at")
    private Instant paymentPaidAt;

    @Column(name = "record_created_date", nullable = false, updatable = false)
    private Instant recordCreatedDate;

    @Column(name = "record_updated_date", nullable = false)
    private Instant recodeUpdatedDate;

    /* ---------- Transient profile holders (used only during registration) ----------
     * phoneNumber USED to be here but is now persisted as the
     * non-transient `phone` column above (unique constraint enforced
     * at registration). Remaining @Transient fields are still
     * convenience holders projected onto the downstream user-service
     * profile DTO, not stored in auth-service. */
    @Transient private String firstName;
    @Transient private String lastName;
    @Transient private String gender;
    @Transient private String address;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        // Build the union: primary role + every role in the multi-role
        // set. On a freshly-V17-backfilled DB these are the same value,
        // but a multi-role user (added in later commits) gets a
        // ROLE_* authority for each.
        Set<Roles> activeRoles = new HashSet<>();
        if (userRole != null) activeRoles.add(userRole);
        if (userRoles != null) activeRoles.addAll(userRoles);
        for (Roles r : activeRoles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.name()));
            r.getPermissions().stream()
                    .map(p -> new SimpleGrantedAuthority(p.name()))
                    .forEach(authorities::add);
        }
        return authorities;
    }

    /**
     * Convenience: returns the union of {@link #userRole} (primary)
     * and {@link #userRoles} (multi-role set). Helpful for DTO
     * projections that want a single source of truth.
     */
    public Set<Roles> getAllRoles() {
        Set<Roles> all = new HashSet<>();
        if (userRole != null) all.add(userRole);
        if (userRoles != null) all.addAll(userRoles);
        return all;
    }

    /**
     * Grant the user an additional role. Idempotent — adding the
     * primary role to the set is a no-op. Use this instead of
     * {@code getUserRoles().add(...)} so the null-safe initialisation
     * happens automatically.
     */
    public void addRole(Roles role) {
        if (role == null) return;
        if (userRoles == null) userRoles = new HashSet<>();
        userRoles.add(role);
    }

    /**
     * V17 sync hook: every time an auth row is INSERTed or UPDATEd,
     * make sure the multi-role set contains the primary role. Without
     * this, code that mutates {@link #userRole} via the Lombok setter
     * (or via the builder at registration) would leave the
     * {@code user_roles} join table out of sync with the legacy column
     * for newly-created rows. The PrePersist + PreUpdate combo
     * guarantees the invariant on every flush.
     */
    @PrePersist
    @PreUpdate
    void syncPrimaryRoleIntoRoleSet() {
        if (userRole != null) addRole(userRole);
    }

    @Override public String getPassword() { return userPassword; }
    @Override public String getUsername() { return userName; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked()  { return Boolean.TRUE.equals(accountNonLocked); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
}
