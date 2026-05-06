package com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceAssignedEvent {
    private String eventType;   // "maintenance.assigned"
    private String requestId;
    private String tenantId;
    private String assignedTo;  // technician/owner id
    private Instant timestamp;
}
