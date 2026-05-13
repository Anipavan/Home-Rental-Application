package com.spa.home_rental_application.property_service.property_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Feign client used by the vacate flow (Issue #5) to check whether a
 * tenant has any outstanding rent invoices for the flat they're trying
 * to vacate. The spec requires every PENDING / OVERDUE invoice to be
 * cleared before the tenant can schedule a vacate.
 *
 * <p>Failures are absorbed by {@link PaymentClientFallback} — payment-service
 * being unreachable shouldn't auto-allow a vacate (a tenant could just
 * wait for an outage and slip through), so the fallback returns a
 * sentinel summary that always BLOCKS the vacate. Loud + safe.
 */
@FeignClient(name = "HRA-payment-service", fallback = PaymentClientFallback.class)
public interface PaymentClient {

    /**
     * Mirrors payment-service {@code GET /payments/flat/{flatId}/unpaid}.
     * Returns the count + total + invoice numbers of PENDING + OVERDUE
     * invoices for the flat. Empty (count=0, total=0) means fully paid.
     */
    @GetMapping("/payments/flat/{flatId}/unpaid")
    UnpaidSummary getUnpaidByFlat(@PathVariable("flatId") String flatId);

    /**
     * Local subset of payment-service's {@code UnpaidSummaryDTO} —
     * keeps us loosely coupled (extra fields the server adds in
     * future are ignored on deserialisation).
     */
    record UnpaidSummary(
            String flatId,
            int unpaidCount,
            BigDecimal totalOutstanding,
            List<String> invoiceNumbers
    ) {
        public boolean isClear() {
            return unpaidCount == 0;
        }

        /**
         * Sentinel returned by the fallback when payment-service is
         * unreachable — pretends there's ₹1 outstanding so the vacate
         * is blocked rather than silently allowed. The error message
         * in the response makes the situation visible to the tenant.
         */
        public static UnpaidSummary unreachable(String flatId) {
            return new UnpaidSummary(flatId, 1, new BigDecimal("1.00"),
                    List.of("PAYMENT_SERVICE_UNREACHABLE"));
        }
    }
}
