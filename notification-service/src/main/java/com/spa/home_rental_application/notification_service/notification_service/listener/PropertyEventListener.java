package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code property-events} for the welcome-email trigger
 * when a tenant moves into a flat.
 */
@Component
@Slf4j
public class PropertyEventListener {

    private final NotificationService notifications;

    public PropertyEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-flat-occupied",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent"}
    )
    public void onFlatOccupied(FlatOccupiedEvent e) {
        if (e == null || !"flat.occupied".equals(e.getEventType())) return;
        log.info("Received {} for flatId={} tenantId={}", e.getEventType(), e.getFlatId(), e.getTenantId());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.LEASE_WELCOME,
                Map.of("flatId",     e.getFlatId() == null ? "" : e.getFlatId(),
                        "rentAmount",e.getRentAmount() == null ? "" : e.getRentAmount(),
                        "startDate", e.getStartDate() == null ? "" : e.getStartDate()));
    }
}
