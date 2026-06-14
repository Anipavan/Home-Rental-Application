package com.spa.home_rental_application.property_service.property_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link PaymentClient}. Failure mode is "fail closed":
 * if payment-service is unreachable, the unpaid summary returns a
 * sentinel that says "₹1 outstanding" so the vacate path is blocked.
 * The tenant sees a clear error and can retry; an outage never
 * silently allows an un-validated vacate.
 */
@Component
@Slf4j
public class PaymentClientFallback implements PaymentClient {

    @Override
    public UnpaidSummary getUnpaidByFlat(String flatId) {
        log.warn("payment-service unavailable — failing closed on dues check for flatId={}", flatId);
        return UnpaidSummary.unreachable(flatId);
    }

    @Override
    public SocietyChargePaymentResponse createSocietyChargePayment(
            CreatePaymentRequest body, String idempotencyKey) {
        // Fail loud here. Returning a sentinel would let the FE
        // navigate to a non-existent paymentId and surface a
        // confusing 404 on the rent-pay page. An IllegalStateException
        // is mapped by property-service's ExceptionClass.handleIllegalState
        // to a 502 Bad Gateway carrying a clear "Couldn't reach payment
        // service" message — exactly what the user needs to see.
        log.warn("payment-service unavailable — couldn't create society-charge Payment for tenant={} flat={} amount={}",
                body.tenantId(), body.flatId(), body.amount());
        throw new IllegalStateException(
                "Couldn't reach payment service to set up the Razorpay flow. Please retry in a moment.");
    }
}
