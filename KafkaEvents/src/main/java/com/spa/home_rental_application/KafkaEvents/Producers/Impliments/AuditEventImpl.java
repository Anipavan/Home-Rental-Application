package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuditServiceEvents.AuditEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Default {@link AuditEventPublisher} backed by Spring Kafka. Picked
 * up by every service that component-scans the
 * {@code com.spa.home_rental_application.KafkaEvents} package (the
 * shared lib pattern every other producer follows).
 *
 * <p>Failures are caught and logged at WARN, never thrown — the
 * business operation has already committed by the time we publish.
 */
@Service
@Slf4j
public class AuditEventImpl implements AuditEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public AuditEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                          KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void publish(AuditEvent event) {
        if (event == null) return;
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        // Partition key: subjectUserId (so all events about the same
        // user land on the same partition for in-order replay during
        // an investigation). Falls back to actorUserId, then
        // resourceId, then the eventType — anything but null.
        String key = firstNonBlank(
                event.getSubjectUserId(),
                event.getActorUserId(),
                event.getResourceId(),
                event.getEventType());
        try {
            kafkaTemplate.send(topics.getAuditTopic(), key, event);
            log.debug("audit→{} {} actor={} subject={} outcome={}",
                    topics.getAuditTopic(), event.getEventType(),
                    event.getActorUserId(), event.getSubjectUserId(),
                    event.getOutcome());
        } catch (Exception ex) {
            // Audit publish must never propagate. A broker outage
            // shouldn't break user-facing operations.
            log.warn("Failed to publish audit event type={} subject={}: {}",
                    event.getEventType(), event.getSubjectUserId(),
                    ex.getMessage());
        }
    }

    @Override
    public void publishSuccess(String eventType, String userId) {
        publish(AuditEvent.builder()
                .eventType(eventType)
                .actorUserId(userId)
                .subjectUserId(userId)
                .outcome("SUCCESS")
                .build());
    }

    @Override
    public void publishSuccess(String eventType, String actorUserId,
                               String subjectUserId, String resourceId,
                               Map<String, String> metadata) {
        publish(AuditEvent.builder()
                .eventType(eventType)
                .actorUserId(actorUserId)
                .subjectUserId(subjectUserId)
                .resourceId(resourceId)
                .outcome("SUCCESS")
                .metadata(metadata)
                .build());
    }

    @Override
    public void publishFailure(String eventType, String actorUserId,
                               String subjectUserId, String reason) {
        publish(AuditEvent.builder()
                .eventType(eventType)
                .actorUserId(actorUserId)
                .subjectUserId(subjectUserId)
                .outcome("FAILURE")
                .reason(reason)
                .build());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "audit";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "audit";
    }
}
