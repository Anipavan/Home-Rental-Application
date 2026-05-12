package com.spa.home_rental_application.auth_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Persisted refresh token. Each successful login creates a row; logout and
 * refresh-then-rotate revoke or replace it. Tokens are stored as opaque
 * UUIDs (not JWTs) so they can be revoked without secret rotation.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_token", columnList = "token", unique = true),
        @Index(name = "idx_refresh_tokens_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    /**
     * Audit H5: client-fingerprint columns. The refresh-then-rotate
     * call compares these against the live request — a token presented
     * from a different IP / user-agent is refused. This narrows the
     * blast radius of an XSS-stolen refresh token (the attacker would
     * also need to match the victim's network + browser fingerprint).
     *
     * <p>The fingerprint check is advisory by default
     * ({@code app.auth.refresh.bind-mode=warn}); flip to {@code strict}
     * via env var for prod-hardened deployments.
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !Boolean.TRUE.equals(revoked) && !isExpired();
    }
}
