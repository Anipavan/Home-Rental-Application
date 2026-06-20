package com.spa.home_rental_application.payment_service.payment_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback for {@link AuthServiceFeign}. The verify endpoint catches
 * exceptions from this fallback and DOES NOT roll back the PAID
 * status — money has already moved at Razorpay. Instead it logs the
 * activation as deferred; {@code RegistrationActivationReconciler}
 * sweeps every 5 min and retries activation for any PAID
 * registration payment whose underlying auth row is still disabled.
 */
@Component
@Slf4j
public class AuthServiceFeignFallbackFactory
        implements FallbackFactory<AuthServiceFeign> {

    @Override
    public AuthServiceFeign create(Throwable cause) {
        log.warn("AuthServiceFeign fallback engaged: {}", cause.toString());
        return (authUserId, body) -> {
            throw new IllegalStateException(
                    "auth-service unavailable — registration activation deferred to reconciler. "
                            + cause.getMessage(), cause);
        };
    }
}
