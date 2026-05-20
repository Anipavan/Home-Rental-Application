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
    /**
     * Owner's auth-service user id, resolved by ReviewService when the
     * review targets a PROPERTY or OWNER. Populated so notification-
     * service can fan the "your tenant left a review" email straight
     * to the owner without itself needing a Feign client to
     * property-service.
     *
     * <p>For PROPERTY reviews: resolved via Building.ownerId
     *   (Feign call to property-service /buildings/{id}).
     * For OWNER reviews: same as targetId — the owner IS the target.
     * For TENANT reviews: left null — notification flow skips them.
     *
     * <p>Nullable for legacy publishers that didn't carry it; the
     * notification listener treats null as "couldn't resolve, skip
     * email" rather than failing the event.
     */
    private String ownerAuthId;
    /**
     * Free-text review body excerpt — first ~200 chars. Used by the
     * owner-side email template's blockquote so the owner sees the
     * tenant's actual words inline. Null when the reviewer didn't
     * write anything (rating-only review).
     */
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime timestamp;
}
