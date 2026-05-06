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
        @Index(name = "idx_user_details_email", columnList = "email", unique = true)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 32)
    private Roles userRole;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @Column(name = "record_created_date", nullable = false, updatable = false)
    private Instant recordCreatedDate;

    @Column(name = "record_updated_date", nullable = false)
    private Instant recodeUpdatedDate;

    /* ---------- Transient profile holders (used only during registration) ---------- */
    @Transient private String firstName;
    @Transient private String lastName;
    @Transient private String gender;
    @Transient private String phoneNumber;
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
