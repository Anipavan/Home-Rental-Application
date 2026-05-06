package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Published by Auth Service on logout (token revocation).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLogoutEvent {
    private String eventType;   // "user.logout"
    private String authUserId;
    private String userName;
    private Instant logoutTime;
    private Instant timestamp;
}
