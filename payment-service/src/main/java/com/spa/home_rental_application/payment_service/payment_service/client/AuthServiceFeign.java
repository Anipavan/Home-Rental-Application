package com.spa.home_rental_application.payment_service.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign into auth-service. Used by the registration-payment flow to
 * flip the disabled auth row back to enabled the moment Razorpay
 * confirms the fee payment. Resolves via Eureka (no hardcoded URL);
 * the {@code FeignGatewaySigningInterceptor} bean (auth-commons) signs
 * every outbound call so the receiving auth-service's
 * {@code GatewayAuthFilter} accepts it as gateway-internal.
 *
 * <p>Wrapped in {@link AuthServiceFeignFallbackFactory} so a 5xx /
 * timeout doesn't leak a raw Feign exception out of the verify call
 * — payment-service marks the payment PAID first and then attempts the
 * activation; if activation fails, the {@code RegistrationActivationReconciler}
 * retries periodically.
 */
@FeignClient(
        name = "HRA-auth-service",
        fallbackFactory = AuthServiceFeignFallbackFactory.class)
public interface AuthServiceFeign {

    @PostMapping("/auth/internal/registration/activate/{authUserId}")
    Map<String, Object> activateRegistration(
            @PathVariable("authUserId") Long authUserId,
            @RequestBody Map<String, String> body);
}
