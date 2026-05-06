package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.MaintenanceServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for maintenance lifecycle events.
 * Topic comes from {@link KafkaTopicProperties#getMaintenanceTopic()}.
 * Message key is {@code requestId} so events for the same maintenance
 * ticket land on the same partition.
 */
@Service
@Slf4j
public class MaintenanceEventImpl implements MaintenanceServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public MaintenanceEventImpl(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendMaintenanceCreated(MaintenanceCreatedEvent e) {
        log.info("→ {} : maintenance.created requestId={}", topics.getMaintenanceTopic(), e.getRequestId());
        kafkaTemplate.send(topics.getMaintenanceTopic(), e.getRequestId(), e);
    }

    @Override
    public void sendMaintenanceAssigned(MaintenanceAssignedEvent e) {
        log.info("→ {} : maintenance.assigned requestId={} assignedTo={}", topics.getMaintenanceTopic(), e.getRequestId(), e.getAssignedTo());
        kafkaTemplate.send(topics.getMaintenanceTopic(), e.getRequestId(), e);
    }

    @Override
    public void sendMaintenanceStatusChanged(MaintenanceStatusChangedEvent e) {
        log.info("→ {} : maintenance.status.changed requestId={} {}→{}", topics.getMaintenanceTopic(), e.getRequestId(), e.getOldStatus(), e.getNewStatus());
        kafkaTemplate.send(topics.getMaintenanceTopic(), e.getRequestId(), e);
    }

    @Override
    public void sendMaintenanceResolved(MaintenanceResolvedEvent e) {
        log.info("→ {} : maintenance.resolved requestId={} resolutionMin={}", topics.getMaintenanceTopic(), e.getRequestId(), e.getResolutionTimeMinutes());
        kafkaTemplate.send(topics.getMaintenanceTopic(), e.getRequestId(), e);
    }

    @Override
    public void sendMaintenanceCommentAdded(MaintenanceCommentAddedEvent e) {
        log.info("→ {} : maintenance.comment.added requestId={}", topics.getMaintenanceTopic(), e.getRequestId());
        kafkaTemplate.send(topics.getMaintenanceTopic(), e.getRequestId(), e);
    }
}
