package com.spa.home_rental_application.payment_service.payment_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link KycClient}. Returns {@code null} so
 * {@code CashfreeVendorService} treats the outage as "prereq
 * unknown" and skips this registration attempt without crashing.
 * Idempotent Kafka trigger will retry on the next event.
 */
@Component
@Slf4j
public class KycClientFallback implements KycClient {

    @Override
    public KycInternal getInternal(String userId) {
        log.warn("kyc-service unavailable — getInternal({}) falling back to null",
                userId);
        return null;
    }
}
