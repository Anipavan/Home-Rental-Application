package com.spa.home_rental_application.user_service.user_service.utils.events;

import lombok.*;

import java.time.Instant;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileUpdatedEvent {
    private String eventType;
    private String userId;
    private String changes;
    private Instant timestamp;
}
