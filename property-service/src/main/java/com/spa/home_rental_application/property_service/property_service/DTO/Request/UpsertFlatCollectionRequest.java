package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maintainer-side per-flat-per-month entry. Creates or updates the
 * {@code maintenance_collection} row keyed by (flat_id, for_month).
 *
 * <p>Typical use: maintainer reads water-meter usage on the 5th, fills
 * in this form for every flat, then hands the printed totals to the
 * residents' WhatsApp group. Once payment integration ships, the same
 * row's amount_paid + payment_id get filled by the Razorpay webhook;
 * for now, the maintainer manually flips status=PAID when they receive
 * the money.
 *
 * <p>{@code notes} carries the line-item breakdown ("water: 200, gas:
 * 150, common-area share: 100"). We don't model line items as
 * separate rows in the MVP — the operational pattern is "maintainer
 * shares a screenshot of the dashboard", and a single notes column is
 * sufficient. A line_item table is the right shape for later if
 * tenants ever start questioning bills.
 */
public record UpsertFlatCollectionRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "forMonth must be YYYY-MM")
        String forMonth,

        @NotNull
        @DecimalMin(value = "0.00", message = "amountDue cannot be negative")
        BigDecimal amountDue,

        /** Optional override of the row's status — defaults to DUE on
         *  create, preserves existing value on update if null. */
        CollectionStatus status,

        /** Optional — line-item description / WhatsApp-ready breakdown. */
        @Size(max = 500)
        String notes,

        /** Optional — when the maintainer is also marking PAID in the
         *  same submit, capture the paid_on date + amount + manual via.
         *  Null on the "just record the dues" path. */
        LocalDate paidOn,

        @DecimalMin(value = "0.00")
        BigDecimal amountPaid,

        @Size(max = 32)
        String paidVia
) {
}
