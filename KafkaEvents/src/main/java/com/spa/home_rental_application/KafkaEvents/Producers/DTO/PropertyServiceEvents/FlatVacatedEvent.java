package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlatVacatedEvent {
    private String eventType;   // "flat.vacated"
    private String flatId;
    /** Human-readable flat number (e.g. "A-301"). Same reasoning as
     *  FlatOccupiedEvent.flatNumber — notification copy should
     *  reference the recognisable number, not the UUID. */
    private String flatNumber;
    private String tenantId;
    private String endDate;     // ISO string
    private Instant timestamp;
}
