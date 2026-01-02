package com.spa.home_rental_application.KafkaEvents.Producers.DTO;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyUpdatedEvent {
    private String eventType;   // "property.updated"
    private String propertyId;
    private String ownerId;
    private Instant timestamp;
}
