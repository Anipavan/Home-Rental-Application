package com.spa.home_rental_application.auth_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Single-use, time-bounded magic-link token emailed to the user
 * after signup. Mirrors the {@link PasswordResetToken} shape so the
 * janitor + invalidate-on-resend idioms carry over.
 *
 * <p>The raw {@link #token} is what we email to the user; on the
 * verify endpoint we look it up by exact match, check
 * {@link #isUsable()}, stamp {@link #consumedAt}, and flip
 * {@code user.emailVerified=true}. A second click on the same link
 * returns 410 GONE so we can distinguish "already verified" from
 * "expired" in the UI.
 */
@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_email_verif_token", columnList = "token", unique = true),
        // The daily janitor's WHERE clause (DELETE … WHERE expires_at
        // < :cutoff) becomes a ranged scan instead of a full sweep.
        @Index(name = "idx_email_verif_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Null until the user clicks the link. Stamped to {@code Instant.now()}
     * on a successful verify. Non-null = consumed = cannot be reused.
     * We keep the row around (rather than deleting on consume) so a
     * second click can surface a distinct "already verified" message.
     * The daily janitor wipes consumed + expired rows after their TTL.
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsable() {
        return !isConsumed() && !isExpired();
    }
}
