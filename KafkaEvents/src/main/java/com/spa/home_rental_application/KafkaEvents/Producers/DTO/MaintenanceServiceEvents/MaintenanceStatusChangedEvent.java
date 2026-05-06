package com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceStatusChangedEvent {
    private String eventType;   // "maintenance.status.changed"
    private String requestId;
    private String tenantId;
    private String oldStatus;
    private String newStatus;
    private String changedBy;
    private Instant timestamp;
}
