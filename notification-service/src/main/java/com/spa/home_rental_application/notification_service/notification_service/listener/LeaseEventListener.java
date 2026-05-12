package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseExpiringEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseSignedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseTerminatedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code lease-events} (published by Lease Service).
 * Every lease milestone fans across {@code INAPP + EMAIL + SMS + WhatsApp}
 * via {@link NotificationService#fanOut} so the tenant hears about the
 * change on whichever channel they've configured.
 * <ul>
 *   <li>{@code lease.signed}     → tenant just got assigned a flat</li>
 *   <li>{@code lease.expiring}   → 60-day warning (cron-driven)</li>
 *   <li>{@code lease.renewed}    → renewal confirmation</li>
 *   <li>{@code lease.terminated} → end-of-tenancy confirmation</li>
 * </ul>
 *
 * <p>Earlier code used {@code sendFromTemplate(EMAIL, ...)} (and
 * sometimes also {@code SMS}) which limited each event to a hand-picked
 * subset of channels. Replaced with {@code fanOut} for the same reason
 * as PaymentEventListener — the product requirement is "tell the tenant
 * on every channel they're reachable on" for material lease changes.
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
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_SIGNED,
                Map.of("leaseNumber", safe(e.getLeaseNumber()),
                        "startDate",  safe(e.getStartDate()),
                        "endDate",    safe(e.getEndDate()),
                        "rentAmount", safe(e.getRentAmount()),
                        "deposit",    safe(e.getSecurityDeposit())));
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
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_EXPIRY,
                Map.of("endDate",         safe(e.getEndDate()),
                        "daysUntilExpiry",safe(e.getDaysUntilExpiry()),
                        "rentAmount",     safe(e.getRentAmount())));
    }

    @KafkaListener(
            topics = "${app.kafka.lease-topic:lease-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-lease-renewed",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents.LeaseRenewedEvent"}
    )
    public void onRenewed(LeaseRenewedEvent e) {
        if (e == null || !"lease.renewed".equals(e.getEventType())) return;
        log.info("Received {} for leaseId={}", e.getEventType(), e.getLeaseId());
        notifications.fanOut(e.getTenantId(),
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
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_TERMINATED,
                Map.of("terminationReason", safe(e.getTerminationReason()),
                        "terminatedOn",     safe(e.getTerminatedOn())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
