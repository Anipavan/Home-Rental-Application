package com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents;

import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileCreatedEvent {
    private String eventType;
    private String userId;
    private String role;
    private LocalDateTime timestamp;
}