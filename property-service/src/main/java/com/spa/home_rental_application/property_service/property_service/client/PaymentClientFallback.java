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
}
