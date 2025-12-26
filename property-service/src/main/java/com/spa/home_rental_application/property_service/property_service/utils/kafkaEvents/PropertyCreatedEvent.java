package com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents;

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
