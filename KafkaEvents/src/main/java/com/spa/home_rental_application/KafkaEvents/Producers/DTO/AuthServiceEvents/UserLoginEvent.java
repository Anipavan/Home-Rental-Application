package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by Auth Service on every successful login. Audit-grade event:
 * Notification + Analytics + Security audit pipelines all consume it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginEvent {
    private String eventType;   // "user.login"
    private String authUserId;
    private String userName;
    private String ipAddress;
    private String userAgent;
    private Instant loginTime;
    private Instant timestamp;
}
