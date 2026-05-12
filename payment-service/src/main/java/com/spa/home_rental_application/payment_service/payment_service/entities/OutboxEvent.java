package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Audit H24: transactional outbox for Kafka events.
 *
 * <p>The problem we're solving: the late-fee scheduler used to flip a
 * payment to OVERDUE and emit {@code payment.overdue} to Kafka in
 * sequence. If Kafka was unreachable between those two steps, the
 * payment state still moved (DB committed) but the notification
 * never fired — the tenant got no overdue ping at all. The
 * scheduler's next run would only retry the FEE part, not the event.
 *
 * <p>Outbox pattern: every state-change-plus-event tuple is written
 * to this table in the SAME transaction as the state change. A
 * separate publisher (OutboxPublisherScheduler) reads pending rows,
 * pushes them to Kafka, and marks them PUBLISHED — at-least-once
 * delivery with no lost events even across broker outages.
 *
 * <p>Indexes:
 *   - (status, created_at) drives the publisher's "next batch" query
 *   - aggregate_id is for ops drilling
 *
 * <p>Schema-evolution friendly: payload stored as JSON text so adding
 * a field to the event class doesn't require a DDL change.
 */
@Entity
@Table(
        name = "payment_outbox_events",
        indexes = {
                @Index(name = "idx_outbox_pending", columnList = "status, created_at"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** e.g. "payment.overdue", "payment.completed". Doubles as the Kafka topic key. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Domain identifier of the state-change source — usually paymentId. Indexed for ops drilling. */
    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    /** Pre-serialized JSON of the Kafka event DTO. */
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    /** PENDING | PUBLISHED | FAILED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Last publisher attempt count — used to back off / quarantine after N failures. */
    @Column(name = "attempts", nullable = false,
            columnDefinition = "NUMBER(10) DEFAULT 0 NOT NULL")
    @Builder.Default
    private Integer attempts = 0;

    /** Most recent error message — null when the row is healthy. */
    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "PENDING";
    }
}
