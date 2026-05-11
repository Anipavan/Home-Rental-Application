package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceAssignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceResolvedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code maintenance-events}. Sends notifications on the
 * three milestone events: created, assigned, resolved. Status-change /
 * comment events are intentionally dropped here to reduce noise.
 */
@Component
@Slf4j
public class MaintenanceEventListener {

    private final NotificationService notifications;

    public MaintenanceEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.maintenance-topic:maintenance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-maintenance-created",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceCreatedEvent"}
    )
    public void onCreated(MaintenanceCreatedEvent e) {
        if (e == null || !"maintenance.created".equals(e.getEventType())) return;
        log.info("Received {} for requestId={}", e.getEventType(), e.getRequestId());
        Map<String, Object> vars = Map.of(
                "requestNumber", safe(e.getRequestNumber()),
                "category",     safe(e.getCategory()),
                "priority",     safe(e.getPriority()));
        // Tenant gets the "we got your request" via INAPP (bell) +
        // EMAIL (if SMTP set up). The owner gets pinged separately on
        // MaintenanceAssignedEvent — maintenance-service auto-routes
        // the ticket to the building owner and fires that event right
        // after MaintenanceCreatedEvent.
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.MAINTENANCE_CREATED, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.maintenance-topic:maintenance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-maintenance-assigned",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceAssignedEvent"}
    )
    public void onAssigned(MaintenanceAssignedEvent e) {
        if (e == null || !"maintenance.assigned".equals(e.getEventType())) return;
        log.info("Received {} for requestId={}", e.getEventType(), e.getRequestId());
        // Notify both tenant + the technician/owner. Each side gets an
        // INAPP entry (bell) plus EMAIL (if SMTP up).
        Map<String, Object> vars = Map.of(
                "requestId", safe(e.getRequestId()),
                "assignedTo", safe(e.getAssignedTo()));
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.MAINTENANCE_ASSIGNED, vars);
        notifications.fanOut(e.getAssignedTo(),
                NotificationCategory.MAINTENANCE_ASSIGNED, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.maintenance-topic:maintenance-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-maintenance-resolved",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.MaintenanceResolvedEvent"}
    )
    public void onResolved(MaintenanceResolvedEvent e) {
        if (e == null || !"maintenance.resolved".equals(e.getEventType())) return;
        log.info("Received {} for requestId={}", e.getEventType(), e.getRequestId());
        // Tenant gets the bell-and-email "your ticket is resolved" ping.
        // (Owner doesn't need one — they resolved it themselves.)
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.MAINTENANCE_RESOLVED,
                Map.of("requestId", safe(e.getRequestId()),
                        "resolutionTimeMinutes", safe(e.getResolutionTimeMinutes())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
