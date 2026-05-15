package com.spa.home_rental_application.payment_service.payment_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link UserClient}. user-service being down must not
 * 500 the payment-details lookup — instead we return the empty
 * sentinel so the calling service can surface a clean
 * "owner's payment details temporarily unavailable" toast.
 */
@Component
@Slf4j
public class UserClientFallback implements UserClient {

    @Override
    public PayoutDetails getPayoutDetails(String userId) {
        log.warn("user-service unavailable — getPayoutDetails({}) falling back to empty",
                userId);
        return PayoutDetails.empty();
    }
}
