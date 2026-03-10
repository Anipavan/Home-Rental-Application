package com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents;

import lombok.*;

import java.time.Instant;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwnerRegisteredEvent {
    private String eventType;
    private String ownerId;
    private String businessName;
    private Instant timestamp;
}

