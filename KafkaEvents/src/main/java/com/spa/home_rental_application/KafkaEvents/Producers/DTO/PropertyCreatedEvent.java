package com.spa.home_rental_application.KafkaEvents.Producers.DTO;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyCreatedEvent {
    private String eventType;   // "property.created"
    private String propertyId;  // buildingId
    private String ownerId;
    private Instant timestamp;
}
