package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by Auth Service when a user signs up while the
 * {@code email_verification_required} toggle is ON, and again whenever
 * the user requests a resend. Notification Service consumes it and
 * emails the magic link.
 *
 * <p>The token is the raw, URL-safe string the user receives in the
 * email. Auth Service stores the same value in
 * {@code email_verification_tokens.token}; the verify endpoint looks
 * it up by exact match, so there's no hash on either side.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationRequestedEvent {
    private String eventType;       // "user.email.verification.requested"
    private String authUserId;
    private String userName;
    private String email;
    private String token;           // raw token to embed in the magic link
    private Instant expiresAt;
    private Instant timestamp;
}
