package com.spa.home_rental_application.property_service.property_service.Entities;

import com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus;
import com.spa.home_rental_application.property_service.property_service.enums.MaintenanceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per per-flat charge line item per month. Post-V5 the
 * unique constraint is (flat_id, for_month, category) so a flat
 * can carry multiple charges in the same month — water bill +
 * maintenance + electricity, each as a separate row with its
 * own status and amount.
 *
 * <p>{@code amount_paid}, {@code paid_via}, {@code payment_id},
 * {@code paid_on} are nullable; populated when the maintainer
 * marks the row PAID (today) or when the future Razorpay webhook
 * sets them automatically. {@code paymentId} is the link into
 * payment-service when integration ships.
 */
@Entity
@Table(
        name = "maintenance_collection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_collection_flat_month_category",
                columnNames = {"flat_id", "for_month", "category"}),
        indexes = {
                @Index(name = "idx_collection_building_month",
                        columnList = "building_id, for_month DESC, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceCollection {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 64, nullable = false)
    private String buildingId;

    @Column(name = "flat_id", length = 64, nullable = false)
    private String flatId;

    /** YYYY-MM. */
    @Column(name = "for_month", length = 7, nullable = false)
    private String forMonth;

    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue;

    /** Populated when {@link #status} = PAID; null otherwise.
     *  Usually equal to {@link #amountDue} but split if the tenant
     *  partially paid (out of scope for MVP). */
    @Column(name = "amount_paid", precision = 12, scale = 2)
    private BigDecimal amountPaid;

    /** Manual marker for now — values like CASH, NEFT, UPI_MANUAL
     *  written by the maintainer when they record the payment.
     *  The payment-integration milestone introduces RAZORPAY_UPI
     *  and similar machine values. */
    @Column(name = "paid_via", length = 32)
    private String paidVia;

    /** Optional link to a payment row in payment-service. Null for
     *  the entire MVP (no Razorpay integration yet); populated by
     *  the future flow. */
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private CollectionStatus status = CollectionStatus.DUE;

    /**
     * Per-flat charge category. Nullable for backward compat with rows
     * created before V4 — the UI renders NULL as OTHER. New writes
     * always carry a value (defaulted to MAINTENANCE in the
     * SetAmountDialog).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private MaintenanceCategory category;

    /** authUserId of the person who last changed the status — the
     *  maintainer when they marked PAID, the owner when they marked
     *  WAIVED. Audit only. */
    @Column(name = "marked_by_user_id", length = 64)
    private String markedByUserId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
