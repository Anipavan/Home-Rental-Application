package com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceCommentAddedEvent {
    private String eventType;   // "maintenance.comment.added"
    private String requestId;
    private String userId;
    private String comment;
    private Instant timestamp;
}
