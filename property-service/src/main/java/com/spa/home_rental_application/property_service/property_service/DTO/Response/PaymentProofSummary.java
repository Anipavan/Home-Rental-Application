package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One tenant-uploaded payment-proof screenshot, enriched with the
 * originating Payment's metadata so the maintainer's lightbox
 * gallery can show "how much was paid, when, and which cycle this
 * proof belongs to" without a second round-trip.
 *
 * <p>Returned as a list on {@link FlatMaintenanceRowResponse}. Empty
 * list means "no proofs attached to any historical payment for
 * this collection row". Ordered newest → oldest by {@code paidAt}
 * (nullable rows land last) so the freshest cycle sits at index 0
 * on the maintainer's dashboard.
 */
public record PaymentProofSummary(
        /** payment-service Payment.id. Used by the frontend to
         *  request the actual blob via
         *  {@code GET /payments/{paymentId}/proof}. */
        String paymentId,

        /** Server-side filename of the proof screenshot. Non-null
         *  by construction — the enrichment skips Payments without
         *  a proof URL. */
        String paymentProofUrl,

        /** Amount the linked Payment settled. Lets the maintainer
         *  reconcile "which cycle is this proof for" at a glance
         *  when a collection row has multiple. */
        BigDecimal amount,

        /** When the Payment was marked PAID (either via gateway
         *  webhook or tenant-report). Nullable on legacy rows that
         *  don't carry a paid timestamp. */
        Instant paidAt
) {}
