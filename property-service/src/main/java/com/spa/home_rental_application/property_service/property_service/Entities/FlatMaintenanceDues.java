package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDateTime;

/**
 * Per-flat override of {@link SocietyConfig#getDefaultPerFlatAmount()}.
 *
 * <p>Larger flats / penthouses / corner units often pay a different
 * monthly society fee. Rather than encoding "amount per sq ft × area"
 * (which fights how owners actually think — they think in absolute
 * rupee amounts agreed at the AGM), we store the flat amount
 * directly.
 *
 * <p>The {@code effective_from_month} column lets a mid-year revision
 * (e.g. society AGM in September voting in a fee hike from October)
 * be modelled correctly:
 *   { flat_id=X, monthly_amount=2000, effective_from_month="2026-01" }
 *   { flat_id=X, monthly_amount=2500, effective_from_month="2026-10" }
 * The service picks the most-recent row whose effective_from_month
 * is &lt;= the month being computed.
 *
 * <p>If no row exists for a (flat, &lt;=month) pair, the building
 * default applies.
 */
@Entity
@Table(
        name = "flat_maintenance_dues",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_flat_dues_effective",
                columnNames = {"flat_id", "effective_from_month"}),
        indexes = {
                @Index(name = "idx_flat_dues_building", columnList = "building_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlatMaintenanceDues {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 36, nullable = false)
    private String buildingId;

    @Column(name = "flat_id", length = 36, nullable = false)
    private String flatId;

    @Column(name = "monthly_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyAmount;

    /** Format: YYYY-MM. The override applies to this month and
     *  every month after, until a row with a later
     *  {@code effective_from_month} for the same flat supersedes it. */
    @Column(name = "effective_from_month", length = 7, nullable = false)
    private String effectiveFromMonth;

    /** Optional free-text reason — "AGM Resolution 5/Sep/2026 hiked
     *  by ₹500", "Reduced during pandemic" etc. Surfaced on the
     *  per-flat dues table in the owner UI for audit. */
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
