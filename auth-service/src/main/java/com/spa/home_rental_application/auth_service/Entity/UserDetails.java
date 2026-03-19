package com.spa.home_rental_application.auth_service.Entity;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "UserDetailsTable")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDetails implements org.springframework.security.core.userdetails.UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userName;
    private String userPassword;
    private  String userRole;
    @Transient
    private  String firstName;
    @Transient
    private  String lastName;
    @Transient
    private  String gender;
    @Transient
    private  String phoneNumber;
    @Transient
    private  String address;
    private Instant recordCreatedDate;
    private  Instant recodeUpdatedDate;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userRole));
    }

    @Override
    public String getPassword() {
        return userPassword;
    }

    @Override
    public String getUsername() {
        return userName;
    }
}
