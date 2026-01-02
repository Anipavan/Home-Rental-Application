package com.spa.home_rental_application.user_service.user_service.utils.events;

import lombok.*;

import java.time.Instant;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileCreatedEvent {
    private String eventType;
    private String userId;
    private String role;
    private Instant timestamp;
}
