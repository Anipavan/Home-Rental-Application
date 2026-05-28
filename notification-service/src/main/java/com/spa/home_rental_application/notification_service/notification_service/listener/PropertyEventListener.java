package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.TenantVacateScheduledEvent;
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
        log.info("Received {} for flatId={} flatNumber={} tenantId={}",
                e.getEventType(), e.getFlatId(), e.getFlatNumber(), e.getTenantId());
        // flatNumber is the human-readable label the tenant recognises
        // (e.g. "A-301"); flatId is the UUID — keep both in vars so
        // templates can reference whichever is appropriate. New
        // templates should prefer {{flatNumber}} for user-visible copy.
        //
        // Issue #7 — include a signInUrl so the lease-welcome message
        // gives the tenant a one-tap path back into the app to view
        // / sign the new lease. Same base URL as the password-reset
        // path, suffixed with /sign-in.
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_WELCOME,
                Map.of("flatId",     safe(e.getFlatId()),
                        "flatNumber", safe(e.getFlatNumber()),
                        "buildingId", safe(e.getBuildingId()),
                        "rentAmount", safe(e.getRentAmount()),
                        "startDate",  safe(e.getStartDate()),
                        "signInUrl",  signInUrl()));
    }

    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-flat-vacated",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent"}
    )
    public void onFlatVacated(FlatVacatedEvent e) {
        if (e == null || !"flat.vacated".equals(e.getEventType())) return;
        log.info("Received {} for flatId={} flatNumber={} tenantId={}",
                e.getEventType(), e.getFlatId(), e.getFlatNumber(), e.getTenantId());
        // Reuse LEASE_TERMINATED so we don't fork the template surface —
        // the body wording is "your tenancy at {flatNumber} ended on {terminatedOn}",
        // which covers both lease-service-driven terminations and
        // property-service-driven vacates from the same template.
        notifications.fanOut(e.getTenantId(),
                NotificationCategory.LEASE_TERMINATED,
                Map.of("flatId",            safe(e.getFlatId()),
                        "flatNumber",       safe(e.getFlatNumber()),
                        "terminatedOn",     safe(e.getEndDate()),
                        "terminationReason","tenancy ended"));
    }

    /**
     * Issue #5 — owner's 10-day-prior vacate warning. Fired by
     * property-service's VacateScheduler. The OWNER is the recipient
     * here (not the tenant), so we fanOut against the owner's
     * authUserId. tenantName is left as a placeholder for now —
     * a future enrichment could call user-service via Feign to fetch
     * the tenant's display name, but for the first cut we render
     * "your tenant" generically.
     */
    @KafkaListener(
            topics = "${app.kafka.property-topic:property-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-tenant-vacate-scheduled",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.TenantVacateScheduledEvent"}
    )
    public void onTenantVacateScheduled(TenantVacateScheduledEvent e) {
        if (e == null || !"tenant.vacate.scheduled".equals(e.getEventType())) return;
        log.info("Received {} for flatId={} flatNumber={} ownerId={} vacateDate={} daysUntil={}",
                e.getEventType(), e.getFlatId(), e.getFlatNumber(),
                e.getOwnerId(), e.getVacateDate(), e.getDaysUntilVacate());
        if (e.getOwnerId() == null || e.getOwnerId().isBlank()) {
            log.warn("Skipping vacate-scheduled notification — event had no ownerId. flatId={}", e.getFlatId());
            return;
        }
        notifications.fanOut(e.getOwnerId(),
                NotificationCategory.TENANT_VACATING_NOTICE,
                Map.of("flatNumber",       safe(e.getFlatNumber()),
                        "vacateDate",      safe(e.getVacateDate()),
                        "daysUntilVacate", String.valueOf(e.getDaysUntilVacate()),
                        // Best-effort tenant name; templates fall back to "your tenant"
                        // when this is blank (Mustache renders missing var as "").
                        "tenantName",      "your tenant"));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }

    /**
     * Resolve the public sign-in URL for the lease-welcome CTA.
     * Built off {@code app.frontend.base-url} so a single env override
     * (FRONTEND_URL) configures every transactional CTA on the platform.
     *
     * <p>Path is {@code /login} — the actual react-router route. Older
     * builds used {@code /sign-in} which 404s.
     */
    private String signInUrl() {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:5173"
                : frontendBaseUrl.replaceAll("/+$", "");
        return base + "/login";
    }

    @org.springframework.beans.factory.annotation.Value(
            "${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;
}
