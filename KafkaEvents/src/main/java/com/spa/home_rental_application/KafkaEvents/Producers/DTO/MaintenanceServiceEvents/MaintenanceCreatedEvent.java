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
    /**
     * Building owner id — populated when the maintenance-service knows
     * who owns the flat. Used by the notification consumer to send a
     * separate "new ticket on your property" bell entry to the owner
     * without going through a follow-up assignment event.
     */
    private String ownerId;
    /**
     * Discriminator: {@code "MAINTENANCE"} or {@code "COMPLAINT"}. Null
     * (legacy) is treated as MAINTENANCE by all downstream consumers.
     */
    private String kind;
    private String category;
    private String priority;
    /** Set when {@code kind == COMPLAINT}; carries the ComplaintCategory name. */
    private String complaintCategory;
    /** Carries the title so the notification consumer can render a useful subject line. */
    private String title;
    private Instant timestamp;
}
