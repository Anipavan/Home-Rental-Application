package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuditServiceEvents.AuditEvent;

import java.util.Map;

/**
 * Single entry point every service uses to publish onto the
 * {@code audit-events} Kafka topic. Lives in the shared lib so a
 * brand-new service can audit-log without each one defining its own
 * producer bean.
 *
 * <p>The {@code publish} variants are intentionally non-blocking,
 * fire-and-forget; callers must NOT make their business operation
 * succeed or fail based on whether the audit row landed. The Kafka
 * producer's retry config is sized to handle transient broker hiccups
 * — anything worse is a cluster outage that platform alerting handles
 * out-of-band.
 */
public interface AuditEventPublisher {

    /**
     * Publish a fully-formed event. Caller is responsible for setting
     * {@code timestamp} only if they want to backdate; otherwise the
     * implementation will set it to {@code Instant.now()} at publish
     * time.
     */
    void publish(AuditEvent event);

    /**
     * Convenience overload for the common case: success outcome,
     * actor == subject, no extra metadata. Equivalent to building an
     * {@code AuditEvent} with eventType + actorUserId + subjectUserId
     * all set to the same value and outcome=SUCCESS.
     */
    void publishSuccess(String eventType, String userId);

    /** Convenience: action targeting a specific resource (paymentId, flatId, …). */
    void publishSuccess(String eventType, String actorUserId,
                        String subjectUserId, String resourceId,
                        Map<String, String> metadata);

    /**
     * Convenience: action that the system denied (authz refused, validation
     * failed, …). Use this rather than {@link #publish} so the outcome
     * field is always set consistently.
     */
    void publishFailure(String eventType, String actorUserId,
                        String subjectUserId, String reason);
}
