package com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceCreatedEvent {
    private String eventType;   // "maintenance.created"
    private String requestId;
    private String requestNumber;
    private String tenantId;
    private String flatId;
    private String category;
    private String priority;
    private Instant timestamp;
}
