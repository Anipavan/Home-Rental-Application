package com.spa.home_rental_application.KafkaEvents.Producers.DTO;

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
    private String tenantId;
    private String endDate;     // ISO string
    private Instant timestamp;
}
