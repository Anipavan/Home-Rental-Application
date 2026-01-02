package com.spa.home_rental_application.KafkaEvents.Producers.DTO;

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
    private String tenantId;
    private String buildingId;
    private Double rentAmount;  // or BigDecimal to match schema
    private String startDate;   // ISO string
    private Instant timestamp;
}
