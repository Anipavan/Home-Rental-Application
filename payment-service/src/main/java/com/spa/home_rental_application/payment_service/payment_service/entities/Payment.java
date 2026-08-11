package com.spa.home_rental_application.payment_service.payment_service.entities;

import com.spa.home_rental_application.payment_service.payment_service.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One rent payment record per (tenant, flat, billing period).
 * Created either by the {@code flat.occupied} consumer (first invoice on
 * tenant move-in) or by an explicit POST /payments call.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_tenant", columnList = "tenant_id"),
        @Index(name = "idx_payments_owner",  columnList = "owner_id"),
        @Index(name = "idx_payments_flat",   columnList = "flat_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_due",    columnList = "due_date"),
        // Composite for RegistrationActivationReconciler — runs every
        // 5 min and asks (source_type, status, payment_date>since).
        // Equality cols first, range col last so Oracle can seek and
        // order in one pass. See V3 migration for the same in SQL.
        @Index(name = "idx_payments_reg_reconciler",
                columnList = "source_type, status, payment_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "flat_id", nullable = false)
    private String flatId;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "late_fee", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Platform commission — the slice of {@link #totalAmount} the
     * platform kept out of the split. Populated at initiate() time via
     * {@code CommissionService.computePlatformFee}. Zero when the
     * owner is on a 0% rule OR when the payment flowed via direct-UPI
     * (i.e. no Cashfree split at all).
     *
     * <p>Owner's share is always {@code totalAmount - platformFee}
     * and is set as the {@code order_splits[0].amount} on the
     * Cashfree order.
     */
    @Column(name = "platform_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal platformFee = BigDecimal.ZERO;

    /**
     * Cashfree vendor_id of the owner at the moment of the split.
     * Historical — snapshot into this row so an owner bank-change
     * doesn't retroactively rewrite which vendor was paid on a
     * given transaction. Nullable for direct-UPI + legacy rows.
     */
    @Column(name = "owner_vendor_id", length = 64)
    private String ownerVendorId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_date")
    private Instant paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** Method chosen when paying. Null until the tenant initiates payment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    /** UPI app — populated only when paymentMethod=UPI. */
    @Enumerated(EnumType.STRING)
    @Column(name = "upi_app", length = 20)
    private UpiApp upiApp;

    /** Wallet provider — populated only when paymentMethod=WALLET. */
    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_provider", length = 20)
    private WalletProvider walletProvider;

    /** Card network — populated only when paymentMethod=CARD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", length = 20)
    private CardNetwork cardNetwork;

    /** Last 4 digits of the card. Never store the full PAN. */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    /** Tenant's UPI VPA (e.g. siva@oksbi). Null for non-UPI methods. */
    @Column(name = "upi_vpa", length = 255)
    private String upiVpa;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /** Gateway-side order id (Razorpay order id, Stripe payment intent id, etc.). */
    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    /** Which gateway processed this payment. */
    @Column(name = "gateway_name", length = 30)
    private String gatewayName;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * What this payment is for. Drives the Rent vs Maintenance tab
     * split on the tenant Payments page + decides which tab the
     * post-Razorpay SuccessView lands on. See V2 migration for the
     * enum-as-string values currently in use:
     * RENT (default) and SOCIETY_CHARGE.
     *
     * <p>Stored as a plain String rather than @Enumerated so the FE /
     * downstream services can introduce new values (e.g. DEPOSIT,
     * MAINTENANCE_REQUEST) without forcing a coupled enum update in
     * every service that deserialises PaymentResponse.
     */
    @Column(name = "source_type", length = 30, nullable = false)
    @Builder.Default
    private String sourceType = "RENT";

    /**
     * URL of the tenant-uploaded payment-proof screenshot (typically
     * a PhonePe / GPay / Paytm success screen). Nullable — set only
     * after the tenant hits POST /payments/{id}/upload-proof. Served
     * back via GET /payments/{id}/proof so the maintainer's Flat
     * charges table can preview it inline. Convenience aid only —
     * the maintainer still verifies against their bank statement
     * before treating the row as truly settled.
     */
    @Column(name = "payment_proof_url", length = 500)
    private String paymentProofUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = PaymentStatus.PENDING;
        if (lateFee == null) lateFee = BigDecimal.ZERO;
        if (totalAmount == null) totalAmount = amount.add(lateFee);
        // Defensive default — sourceType is NOT NULL in V2 onwards;
        // if a code path forgets to set it (e.g. legacy admin POST
        // /payments before this commit), default to RENT.
        if (sourceType == null || sourceType.isBlank()) sourceType = "RENT";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (totalAmount == null) totalAmount = amount.add(lateFee == null ? BigDecimal.ZERO : lateFee);
    }
}
