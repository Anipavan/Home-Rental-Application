package com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/** Published when an admin / system moderates a review (approves / rejects). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewModeratedEvent {
    private String eventType;
    private String reviewId;
    private String moderatorId;
    private String moderationDecision;   // APPROVED | REJECTED | FLAGGED
    private String moderationReason;
    private LocalDateTime moderatedAt;
    private LocalDateTime timestamp;
}
