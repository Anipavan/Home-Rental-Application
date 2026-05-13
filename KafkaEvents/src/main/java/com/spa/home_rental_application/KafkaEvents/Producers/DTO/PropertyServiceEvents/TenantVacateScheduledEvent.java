package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Fired by property-service's {@code VacateScheduler} 10 days before
 * a tenant's scheduled vacate date — gives the owner time to plan
 * re-listing, walkthrough, deposit return, etc. Consumed by
 * notification-service's PropertyEventListener which fans the
 * notification out across the owner's reachable channels
 * (INAPP + EMAIL + SMS + WhatsApp) via
 * {@code NotificationCategory.TENANT_VACATING_NOTICE}.
 *
 * <p>This is DIFFERENT from {@code flat.vacated} — that one fires
 * AFTER the vacate completes. This one fires DURING the 60-day
 * notice window, 10 days before the actual move-out, so the owner
 * has lead time.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantVacateScheduledEvent {
    private String eventType;        // "tenant.vacate.scheduled"
    private String flatId;
    /** Human-readable flat number (e.g. "A-301") for the notification body. */
    private String flatNumber;
    /** authUserId of the OWNER — recipient of the notification. */
    private String ownerId;
    /** authUserId of the departing tenant — used to look up their name. */
    private String tenantId;
    /** ISO date the tenant will actually move out. */
    private String vacateDate;
    /** How many days until vacate at the time of this event (typically 10). */
    private int daysUntilVacate;
    private Instant timestamp;
}
