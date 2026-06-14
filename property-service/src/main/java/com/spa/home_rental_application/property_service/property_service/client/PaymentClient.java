package com.spa.home_rental_application.property_service.property_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * Create a Payment row backing a society maintenance charge — the
     * resident clicks "Pay all" on /app/society/pay-all and we need a
     * paymentId they can funnel through the existing Razorpay flow
     * (/app/payments/{id}/pay). Calls the tenant-accessible
     * {@code POST /payments/society-charge} endpoint, which is the
     * sibling of the admin-only {@code POST /payments} and shares the
     * same {@code CreatePaymentRequest} body shape.
     *
     * <p>The optional {@code Idempotency-Key} header guards against
     * fast double-clicks creating duplicate Razorpay orders.
     */
    @PostMapping("/payments/society-charge")
    SocietyChargePaymentResponse createSocietyChargePayment(
            @RequestBody CreatePaymentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey);

    /**
     * Body mirror of payment-service's {@code CreatePaymentRequest}.
     * Inlined as a nested record so this client module stays
     * dependency-free of payment-service's DTOs.
     */
    record CreatePaymentRequest(
            String tenantId,
            String flatId,
            String ownerId,
            BigDecimal amount,
            LocalDate dueDate
    ) {}

    /**
     * Subset of payment-service's {@code PaymentResponse} — we only need
     * the new paymentId so we can hand it back to the FE for redirect.
     * Extra fields are ignored at deserialisation.
     */
    record SocietyChargePaymentResponse(
            String id,
            String status,
            BigDecimal totalAmount
    ) {}

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
