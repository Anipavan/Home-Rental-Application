package com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuditServiceEvents;

import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Single event-shape on the dedicated {@code audit-events} Kafka
 * topic. Every security-relevant mutation across the platform
 * (login, logout, password reset, role change, lease termination,
 * payment-as-cash recorded, bank account add/update, ...) publishes
 * one of these so the operations team has one append-only audit
 * trail to query during incidents.
 *
 * <p>Producers should treat publish failures as non-fatal: the
 * business operation has already committed; losing the audit row
 * is bad but not catastrophic. The Kafka producer config retries
 * three times with the standard back-off — anything worse than that
 * is a Kafka cluster outage and gets caught by service-level
 * alerting separately.
 *
 * <p>{@code metadata} is a free-form map for action-specific
 * detail (the new role on a role-change, the flat ID on a
 * vacate-scheduled, ...). Keep it small (< 8 keys) and human-
 * readable; this is what an on-call engineer scrolls through during
 * an incident, not a structured-query target.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    /**
     * Event-type discriminator. Convention is
     * {@code <domain>.<action>}, e.g. {@code auth.login.success},
     * {@code auth.login.failed}, {@code auth.password.reset},
     * {@code user.role.changed}, {@code lease.terminated},
     * {@code payment.cash.recorded}, {@code bank-account.updated}.
     */
    private String eventType;

    /**
     * The authUserId of the actor performing the action. Anonymous
     * actions (failed login before we know who's trying) leave this
     * blank — the {@code subjectUserId} below identifies the target.
     */
    private String actorUserId;

    /**
     * The authUserId of the resource owner / target. For
     * self-mutations (a tenant editing their own bank account)
     * actor == subject. For admin-driven actions (admin disabling a
     * user) they differ — the audit trail captures both so
     * post-incident review knows who did what to whom.
     */
    private String subjectUserId;

    /**
     * Optional secondary id when the action targets a non-user
     * resource (paymentId, flatId, leaseId, documentId, ...).
     */
    private String resourceId;

    /**
     * {@code SUCCESS} | {@code FAILURE} | {@code DENIED}. FAILURE is
     * for "the user tried and it broke" (wrong password, validation
     * failure); DENIED is for "the user tried and authz refused"
     * (non-admin hitting an admin endpoint).
     */
    private String outcome;

    /** Free-text reason — typically the exception message when outcome != SUCCESS. */
    private String reason;

    /**
     * Where the request came from. The gateway stamps the original
     * IP from X-Forwarded-For onto every internal request; producers
     * should propagate it here so the audit trail tells us the
     * client IP, not the internal Spring service IP.
     */
    private String clientIp;

    /** User-Agent header as-seen. Useful for "weird browser" forensics. */
    private String userAgent;

    /**
     * Distributed-trace id of the request that produced this event.
     * Lets an investigator pivot from "audit row" → "full request
     * trace in the OTLP collector" in one click.
     */
    private String traceId;

    /** Action-specific detail. Keep small + readable. */
    private Map<String, String> metadata;

    /** When the action happened. Set at publish time. */
    private Instant timestamp;
}
