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
    private Instant timestamp;
}
