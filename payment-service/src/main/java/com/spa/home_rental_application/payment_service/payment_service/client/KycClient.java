package com.spa.home_rental_application.payment_service.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to kyc-service, used by
 * {@code CashfreeVendorService} to fetch the owner's raw PAN and
 * legal name for Cashfree Easy Split vendor registration.
 *
 * <p>Falls back to null on outage (see {@link KycClientFallback}) —
 * the caller retries on the next Kafka trigger.
 */
@FeignClient(name = "HRA-kyc-service", fallback = KycClientFallback.class)
public interface KycClient {

    @GetMapping("/kyc/internal/{userId}")
    KycInternal getInternal(@PathVariable("userId") String userId);

    /** Wire shape mirrors kyc-service's KycInternalDto. */
    record KycInternal(
            String userId,
            String panNumber,
            String panHolderName,
            Boolean verified,
            String verificationStatus
    ) {}
}
