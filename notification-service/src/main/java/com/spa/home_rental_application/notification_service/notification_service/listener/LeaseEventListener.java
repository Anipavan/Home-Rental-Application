package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code lease-events} (published by Lease Service).
 * <ul>
 *   <li>{@code lease.signed}     → welcome email + SMS to tenant</li>
 *   <li>{@code lease.expiring}   → warning to tenant (cron-driven, 60 days before)</li>
 *   <li>{@code lease.renewed}    → confirmation to tenant</li>
 *   <li>{@code lease.terminated} → confirmation to tenant</li>
 * </ul>
 */
@Component
@Slf4j
public class LeaseEventListener {

    private final NotificationService notifications;

    public LeaseEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.lease-topic:lease-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-lease-signed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent"}
    )
    public void onSigned(LeaseSignedEvent e) {
        if (e == null || !"lease.signed".equals(e.getEventType())) return;
        log.info("Received {} for leaseId={}", e.getEventType(), e.getLeaseId());
        Map<String, Object> vars = Map.of(
                "leaseNumber", safe(e.getLeaseNumber()),
                "startDate",   safe(e.getStartDate()),
                "endDate",     safe(e.getEndDate()),
                "rentAmount",  safe(e.getRentAmount()),
                "deposit",     safe(e.getSecurityDeposit()));
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.LEASE_SIGNED, vars);
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.SMS,
                NotificationCategory.LEASE_SIGNED, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.lease-topic:lease-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-lease-expiring",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent"}
    )
    public void onExpiring(LeaseExpiringEvent e) {
        if (e == null || !"lease.expiring".equals(e.getEventType())) return;
        log.info("Received {} for leaseId={} daysLeft={}",
                e.getEventType(), e.getLeaseId(), e.getDaysUntilExpiry());
        Map<String, Object> vars = Map.of(
                "endDate",         safe(e.getEndDate()),
                "daysUntilExpiry", safe(e.getDaysUntilExpiry()),
                "rentAmount",      safe(e.getRentAmount()));
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.LEASE_EXPIRY, vars);
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.SMS,
                NotificationCategory.LEASE_EXPIRY, vars);
    }

    @KafkaListener(
            topics = "${app.kafka.lease-topic:lease-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-lease-renewed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent"}
    )
    public void onRenewed(LeaseRenewedEvent e) {
        if (e == null || !"lease.renewed".equals(e.getEventType())) return;
        log.info("Received {} for leaseId={}", e.getEventType(), e.getLeaseId());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.LEASE_RENEWED,
                Map.of("previousEndDate", safe(e.getPreviousEndDate()),
                        "newEndDate",     safe(e.getNewEndDate()),
                        "previousRent",   safe(e.getPreviousRent()),
                        "newRent",        safe(e.getNewRent())));
    }

    @KafkaListener(
            topics = "${app.kafka.lease-topic:lease-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-lease-terminated",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent"}
    )
    public void onTerminated(LeaseTerminatedEvent e) {
        if (e == null || !"lease.terminated".equals(e.getEventType())) return;
        log.info("Received {} for leaseId={} reason={}",
                e.getEventType(), e.getLeaseId(), e.getTerminationReason());
        notifications.sendFromTemplate(e.getTenantId(), NotificationType.EMAIL,
                NotificationCategory.LEASE_TERMINATED,
                Map.of("terminationReason", safe(e.getTerminationReason()),
                        "terminatedOn",     safe(e.getTerminatedOn())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
