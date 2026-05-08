package com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/** Published when a tenant or owner submits a review. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSubmittedEvent {
    private String eventType;
    private String reviewId;
    private String reviewerId;
    private String reviewerType;     // TENANT | OWNER
    private String targetId;
    private String targetType;       // PROPERTY | OWNER | TENANT
    private Integer rating;          // 1-5
    private LocalDateTime createdAt;
    private LocalDateTime timestamp;
}
