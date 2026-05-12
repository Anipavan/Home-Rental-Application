package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by Auth Service when a new user account is successfully created.
 * Consumed by User Service (to create the linked profile) and Notification
 * Service (to send the welcome email).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private String eventType;   // "user.registered"
    private String authUserId;  // primary key in Auth Service
    private String userName;
    private String role;        // ADMIN | OWNER | TENANT
    private String email;
    /**
     * Mobile number captured at registration. Optional — but when
     * present, notification-service seeds it into the user's
     * NotificationPreference row so the registration welcome can
     * actually fan out to SMS + WhatsApp instead of email-only.
     * Format is whatever the user typed (we keep it tolerant: +91 …,
     * 10-digit local, etc.); Twilio adapters do the E.164 normalisation.
     */
    private String phone;
    private Instant timestamp;
}
