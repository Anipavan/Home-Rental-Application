package com.spa.home_rental_application.auth_service.Service.external;

import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client into payment-service. Resolves via Eureka. Used by the
 * paid-registration flow to pre-create a PENDING Payment row that
 * payment-service later upgrades to PAID when Razorpay confirms.
 *
 * <p>The endpoint hit here ({@code /payments/registration/create-pending})
 * is gateway-internal — payment-service permits it only when the
 * inbound request carries a valid {@code X-Internal-Auth-Sig} header.
 * The {@code FeignGatewaySigningInterceptor} bean (auth-commons) signs
 * every outbound Feign call automatically, so no extra header-plumbing
 * is needed here.
 */
@FeignClient(
        name = "HRA-payment-service",
        fallbackFactory = PaymentServiceFeignFallbackFactory.class)
public interface PaymentServiceFeign {

    @PostMapping("/payments/registration/create-pending")
    CreateRegistrationPaymentResponse createPendingRegistrationPayment(
            @RequestBody CreateRegistrationPaymentRequest request);
}
