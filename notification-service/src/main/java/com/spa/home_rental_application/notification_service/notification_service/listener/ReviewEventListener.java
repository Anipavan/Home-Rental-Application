package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewSubmittedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Subscribes to {@code review-events}. Currently handles:
 * <ul>
 *   <li>{@code review.submitted} — when a tenant or owner posts a
 *       new review. The notification recipient depends on
 *       {@code targetType}:
 *       <ul>
 *         <li><b>PROPERTY</b> / <b>OWNER</b> — the targetId is the
 *             owner (or the building owner) and we ping them with
 *             a {@link NotificationCategory#REVIEW_RECEIVED_FOR_OWNER}
 *             email containing the rating + optional comment +
 *             a CTA back to the review page.</li>
 *         <li><b>TENANT</b> — the targetId is the tenant. We
 *             deliberately skip this for now — owners reviewing
 *             tenants is a less-common flow and surfacing it would
 *             be noise until we add a tenant-side reviews view.</li>
 *       </ul></li>
 * </ul>
 *
 * <p>Why we don't notify the reviewer themselves: the review form's
 * own success toast covers that side; emailing them their own review
 * back is noise.
 */
@Component
@Slf4j
public class ReviewEventListener {

    private final NotificationService notifications;

    public ReviewEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.review-topic:review-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-review-submitted",
            properties = {
                    "spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewSubmittedEvent"
            }
    )
    public void onSubmitted(ReviewSubmittedEvent e) {
        if (e == null || !"review.submitted".equals(e.getEventType())) return;
        log.info("Received {} for reviewId={} target={}/{}",
                e.getEventType(), e.getReviewId(), e.getTargetType(), e.getTargetId());

        // We only notify when the review targets an OWNER or a
        // PROPERTY — both resolve to "owner sees this". Tenant-side
        // reviews are skipped (see class-level javadoc).
        String targetType = e.getTargetType() == null ? "" : e.getTargetType();
        if (!"OWNER".equalsIgnoreCase(targetType) && !"PROPERTY".equalsIgnoreCase(targetType)) {
            log.debug("Skipping review notification — unsupported targetType={}", targetType);
            return;
        }

        // Recipient resolution — the producer-side ReviewService
        // populates {@code ownerAuthId} on the event for both PROPERTY
        // and OWNER target types. We prefer that explicit field over
        // targetId because:
        //   - PROPERTY reviews carry targetId = buildingId (NOT an
        //     auth user id), and fanOut on a buildingId would silently
        //     fail to match any UserNotificationPreference row.
        //   - OWNER reviews have targetId == ownerAuthId already, so
        //     this just makes the contract explicit.
        // Legacy publishers that don't carry ownerAuthId fall through
        // to targetId, with the same caveat — works for OWNER target,
        // silently no-ops for PROPERTY. Worth keeping for old events
        // already on disk.
        String ownerId = e.getOwnerAuthId();
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = e.getTargetId();
            log.debug("ownerAuthId missing on event {} — falling back to targetId {}",
                    e.getReviewId(), ownerId);
        }
        if (ownerId == null || ownerId.isBlank()) {
            log.warn("Skipping review notification {} — no owner id resolvable", e.getReviewId());
            return;
        }

        // Build vars. {@code comment} comes from the event now (~280
        // char excerpt). tenantName + flatNumber are not yet on the
        // event — the owner-side template handles their absence via
        // Mustache truthy sections so the email still renders.
        Map<String, Object> vars = new HashMap<>();
        vars.put("rating", safe(e.getRating()));
        vars.put("reviewerId", safe(e.getReviewerId()));
        vars.put("reviewerType", safe(e.getReviewerType()));
        vars.put("targetType", safe(e.getTargetType()));
        vars.put("comment", safe(e.getComment()));
        // Optional fields — empty strings so the Mustache sections
        // hide cleanly rather than rendering [tenantName] etc.
        vars.put("tenantName", "");
        vars.put("flatNumber", "");

        log.info("Fanning REVIEW_RECEIVED_FOR_OWNER to ownerAuthId={} (review={})",
                ownerId, e.getReviewId());
        notifications.fanOut(ownerId,
                NotificationCategory.REVIEW_RECEIVED_FOR_OWNER, vars);
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }
}
