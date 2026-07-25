package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code POST /society/{buildingId}/flats/{flatId}/paid-toggle}.
 *
 * <p>Powers the maintainer's PAID / DUE toggle on the Flat charges
 * table. The two directions do very different things:
 *
 * <ul>
 *   <li><b>paid = true (NO → YES)</b>: mark every real charge for
 *       (flat, month) as PAID with paidOn=today, paidVia=CASH.
 *       Linked Payment rows (if any) are left alone — this is a
 *       manual "I saw the cash in hand" assertion, not a payment-
 *       service transaction.</li>
 *   <li><b>paid = false (YES → NO)</b>: revoke every linked Payment
 *       via {@code payment-service /revert-to-due} (clears
 *       paymentProofUrl + status), then reset every collection row
 *       to DUE with cleared paidOn/paidVia/amountPaid AND cleared
 *       paymentId so a subsequent pay attempt gets a clean slate.</li>
 * </ul>
 */
public record FlatPaidToggleRequest(
        @NotBlank(message = "month is mandatory (YYYY-MM)") String month,
        @NotNull(message = "paid flag is mandatory")        Boolean paid
) {}
