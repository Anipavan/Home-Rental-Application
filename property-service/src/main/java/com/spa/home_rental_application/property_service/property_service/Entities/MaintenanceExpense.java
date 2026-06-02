package com.spa.home_rental_application.property_service.property_service.Entities;

import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
 * A single common-area expense record. The unit of work for the
 * maintainer dashboard ("add water bill for May", "add security
 * salary for May"). One row = one entry on the society ledger.
 *
 * <p>The {@code expense_month} (YYYY-MM) and {@code paid_on_date}
 * are intentionally separate:
 * <ul>
 *   <li>{@code expense_month} is what month this expense is BUDGETED
 *       AGAINST — used for the ledger's monthly grouping. A maintainer
 *       may pay the May water bill on the 7th of June; that's still
 *       a May expense on the ledger.</li>
 *   <li>{@code paid_on_date} is the actual bank-debit / cash-paid
 *       date. Used for reconciliation against bank statements and
 *       cash-flow-by-day analysis (rare).</li>
 * </ul>
 *
 * <p>{@code receipt_doc_id} points at a document-service blob (the
 * uploaded bill PDF / photo). Nullable — small recurring expenses
 * may not have a paper trail (cash payment to the part-time
 * gardener). When set, the tenant ledger can offer a "View receipt"
 * link that streams via document-service's HMAC-signed URL.
 */
@Entity
@Table(
        name = "maintenance_expense",
        indexes = {
                @Index(name = "idx_expense_building_month",
                        columnList = "building_id, expense_month DESC, paid_on_date DESC")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceExpense {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 36, nullable = false)
    private String buildingId;

    /** YYYY-MM — the month the ledger buckets this expense into. */
    @Column(name = "expense_month", length = 7, nullable = false)
    private String expenseMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32, nullable = false)
    private ExpenseCategory category;

    /** Free-form vendor / line-item label — "BWSSB water bill",
     *  "Ramesh — security salary", "Cleaning supplies — Big Bazaar".
     *  Renders next to the amount on the ledger row. */
    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Who got paid — vendor / staff member / utility company. PII
     *  for staff names, so log-masked in the controller layer. */
    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    /** Actual debit date. May lag {@code expense_month} by days. */
    @Column(name = "paid_on_date", nullable = false)
    private LocalDate paidOnDate;

    /** Document-service blob id for the uploaded bill / receipt
     *  PDF / photo. Null if no receipt uploaded. */
    @Column(name = "receipt_doc_id", length = 36)
    private String receiptDocId;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** authUserId of the maintainer / owner who added this row.
     *  Audit trail for the public ledger ("Added by Ramesh on 5 Jun"). */
    @Column(name = "added_by_user_id", length = 64, nullable = false)
    private String addedByUserId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
