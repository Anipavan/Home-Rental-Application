package com.spa.home_rental_application.auth_service.auth_service.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table (name ="User_Passcode_Details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long user_id;

    private String userName;
    private String userPassword;
    private String confirmUserPassword;

    private Instant userCreatedDate;
    private Instant userUpdatedDate;

    @OneToMany
    private Set<Role> roles = new HashSet<>();
    @Transient
    private String email;
    @Transient
    private String firstName;
    @Transient
    private String lastName;

    @Temporal(TemporalType.DATE)
    private java.util.Date dateOfBirth;
    @Transient
    private String phoneNumber;

    @Transient
    private String gender;

    @Transient
    private String address;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) () -> role.getRoleName())
                .toList();
    }
    @Override
    public String getPassword() {
        return this.userPassword;
    }

    @Override
    public String getUsername() {
        return this.userName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
