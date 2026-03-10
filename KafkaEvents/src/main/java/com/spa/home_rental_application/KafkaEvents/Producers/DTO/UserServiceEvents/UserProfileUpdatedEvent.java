package com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents;

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
