package com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceResolvedEvent {
    private String eventType;   // "maintenance.resolved"
    private String requestId;
    private String tenantId;
    private long   resolutionTimeMinutes;   // createdAt → resolvedAt
    private Instant timestamp;
}
