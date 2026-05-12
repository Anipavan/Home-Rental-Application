package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Idempotency log for inbound payment-gateway webhooks. Razorpay /
 * Stripe both retry webhooks aggressively on network blips, so the
 * webhook handler must guarantee that processing the same event twice
 * doesn't double-credit a payment.
 *
 * <p>Strategy: every accepted webhook (signature-verified) writes a row
 * keyed on {@code (gatewayName, eventKey)} BEFORE any state change. A
 * duplicate event collides on the unique constraint, the second handler
 * exits early with a 200, and the payment is never touched twice.
 *
 * <p>{@code eventKey} is derived per-gateway:
 *  - Razorpay: the {@code payload.payment.entity.id} (always unique per attempt)
 *  - Stripe:   the top-level {@code id} (evt_xxx)
 *  - MockPaymentGateway: a stable hash of (orderId + transactionId)
 *
 * <p>We DON'T use {@code paymentId} as the idempotency key because a
 * single payment legitimately produces multiple webhooks (authorized,
 * captured, failed → re-authorized).
 */
@Entity
@Table(
        name = "processed_webhooks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_webhook_event",
                columnNames = {"gateway_name", "event_key"}),
        indexes = @Index(name = "idx_processed_webhook_processed_at", columnList = "processed_at")
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcessedWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** {@code "razorpay" | "stripe" | "mock"}. */
    @Column(name = "gateway_name", nullable = false, length = 32)
    private String gatewayName;

    /** Per-gateway unique event identifier. See class javadoc for the per-gateway derivation. */
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    /** Resolved local payment id (may be null on hospital records for events we couldn't match). */
    @Column(name = "payment_id", length = 64)
    private String paymentId;

    /** Razorpay/Stripe transaction id captured from the payload. */
    @Column(name = "transaction_id", length = 200)
    private String transactionId;

    /** PROCESSED | DUPLICATE | FAILED — for ops drilling. */
    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
