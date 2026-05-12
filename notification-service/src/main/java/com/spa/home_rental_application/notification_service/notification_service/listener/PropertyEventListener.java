package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code property-events} for tenant move-in / move-out
 * triggers.
 *
 * <ul>
 *   <li>{@code flat.occupied} → tenant just got assigned a flat. Fan
 *       a {@code LEASE_WELCOME} notification across every reachable
 *       channel so the tenant hears about their new lease on whichever
 *       channel they've configured.</li>
 *   <li>{@code flat.vacated}  → tenant just moved out (lease ended,
 *       voluntary departure, or eviction). Fan a {@code LEASE_TERMINATED}
 *       notification so they get a confirmation across every channel —
 *       same product requirement, opposite direction.</li>
 * </ul>
 *
 * <p>{@link NotificationService#fanOut} does the channel-by-channel
 * dispatch + opt-out / missing-recipient handling; no branching needed
 * here.
 *
 * <p>Earlier code only listened for {@code flat.occupied} — the vacate
 * side was being emitted by property-service but no listener consumed
 * it, so departing tenants got no confirmation. Added the symmetric
 * {@link #onFlatVacated} handler to close that gap.
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
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_WELCOME,
                Map.of("flatId",     safe(e.getFlatId()),
                        "buildingId", safe(e.getBuildingId()),
                        "rentAmount", safe(e.getRentAmount()),
                        "startDate",  safe(e.getStartDate())));
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-flat-vacated",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent"}
    )
    public void onFlatVacated(FlatVacatedEvent e) {
        if (e == null || !"flat.vacated".equals(e.getEventType())) return;
        log.info("Received {} for flatId={} tenantId={}", e.getEventType(), e.getFlatId(), e.getTenantId());
        // Reuse LEASE_TERMINATED so we don't fork the template surface —
        // the body wording is "your tenancy at {flatId} ended on {endDate}",
        // which covers both lease-service-driven terminations and
        // property-service-driven vacates from the same template.
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_TERMINATED,
                Map.of("flatId",            safe(e.getFlatId()),
                        "terminatedOn",     safe(e.getEndDate()),
                        "terminationReason","tenancy ended"));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
