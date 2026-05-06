package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by Auth Service when a forgot-password flow is started. The
 * Notification Service consumes it and emails the reset link to the user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestedEvent {
    private String eventType;   // "user.password.reset.requested"
    private String authUserId;
    private String userName;
    private String email;
    private String resetToken;  // single-use, time-bounded
    private Instant expiresAt;
    private Instant timestamp;
}
