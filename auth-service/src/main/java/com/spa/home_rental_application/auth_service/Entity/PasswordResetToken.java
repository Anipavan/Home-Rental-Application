package com.spa.home_rental_application.auth_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Single-use, time-bounded token used by the forgot-password flow.
 * The Notification Service emails the raw token to the user; the user
 * presents it back to /auth/reset-password to set a new password.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_pw_reset_token", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !Boolean.TRUE.equals(used) && !isExpired();
    }
}
