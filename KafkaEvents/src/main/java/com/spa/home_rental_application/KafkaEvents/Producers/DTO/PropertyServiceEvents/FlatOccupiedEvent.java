package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlatOccupiedEvent {
    private String eventType;   // "flat.occupied"
    private String flatId;
    /** Human-readable flat number (e.g. "A-301"). Separate from
     *  flatId (UUID) so notification templates can render something
     *  the tenant actually recognises rather than the raw id. */
    private String flatNumber;
    private String tenantId;
    private String buildingId;
    private Double rentAmount;
    private String startDate;
    private Instant timestamp;
}
