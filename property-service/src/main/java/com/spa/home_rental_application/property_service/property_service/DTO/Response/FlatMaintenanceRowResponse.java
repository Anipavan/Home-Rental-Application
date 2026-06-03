package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import com.spa.home_rental_application.property_service.property_service.enums.MaintenanceCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row in the maintainer's per-flat dashboard for a given month.
 * Owner reads the same payload for the "all flats / dues / collected"
 * read-only view on the society page.
 *
 * <p>The {@code monthAmount} is the maintainer-set per-flat amount for
 * {@code forMonth} when a {@code maintenance_collection} row exists,
 * else falls back to the building's {@code default_per_flat_amount}.
 * The frontend doesn't need to know the resolution logic — backend
 * surfaces the resolved number.
 *
 * <p>{@code status} mirrors {@link
 * com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus}
 * — DUE / PAID / WAIVED / OVERDUE. NEW_FLAT here means "no
 * maintenance_collection row exists yet for this (flat, month) pair";
 * the maintainer creates one by submitting the "Set amount" form.
 *
 * <p>{@code notes} is the maintainer's free-form line-item description
 * (e.g. "water 200 + gas 150 + lift hike share 100"). Pure bookkeeping
 * — money doesn't flow through this layer in the MVP.
 */
@Builder
public record FlatMaintenanceRowResponse(
        String flatId,
        String flatNumber,

        String tenantUserId,
        String tenantName,

        /** Resolved per-flat amount for the month — either the collection
         *  row's amount_due, or the building's default if no row yet. */
        BigDecimal monthAmount,

        /** DUE | PAID | WAIVED | OVERDUE | NEW_FLAT (frontend label). */
        String status,

        /** Building's default per-flat amount — render alongside
         *  monthAmount when monthAmount overrides the default. */
        BigDecimal defaultAmount,

        /** YYYY-MM the row is for; echoes the requested month. */
        String forMonth,

        /** Optional notes the maintainer attached to the collection. */
        String notes,

        /** Paid date when status=PAID, else null. */
        LocalDate paidOn,

        /** Manual payment method label when status=PAID
         *  (CASH / NEFT / UPI_MANUAL). Null otherwise. */
        String paidVia,

        BigDecimal amountPaid,

        /** Per-flat charge category. Null for pre-V4 rows; the UI
         *  renders NULL as OTHER. */
        MaintenanceCategory category
) {
}
