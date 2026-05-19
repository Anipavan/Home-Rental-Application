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

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 32)
    private Roles userRole;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
        authorities.add(new SimpleGrantedAuthority("ROLE_" + userRole.name()));
        Set<SimpleGrantedAuthority> permissionAuthorities = userRole.getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.name()))
                .collect(Collectors.toSet());
        authorities.addAll(permissionAuthorities);
        return authorities;
    }

    @Override public String getPassword() { return userPassword; }
    @Override public String getUsername() { return userName; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked()  { return Boolean.TRUE.equals(accountNonLocked); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
}
