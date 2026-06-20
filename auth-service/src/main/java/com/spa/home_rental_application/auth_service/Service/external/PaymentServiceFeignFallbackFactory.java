package com.spa.home_rental_application.auth_service.Service.external;

import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.auth_service.Dto.External.CreateRegistrationPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link PaymentServiceFeign} — same shape as
 * {@code UserServiceFeignFallbackFactory}. On any downstream failure
 * (circuit open, 5xx, timeout), we surface a clear runtime exception so
 * the surrounding {@code @Transactional registerPending} rolls back
 * the half-created auth row instead of leaving a disabled user
 * stranded with no payment to settle.
 */
@Slf4j
@Component
public class PaymentServiceFeignFallbackFactory
        implements FallbackFactory<PaymentServiceFeign> {

    @Override
    public PaymentServiceFeign create(Throwable cause) {
        log.warn("PaymentServiceFeign fallback engaged: {}", cause.toString());
        return new PaymentServiceFeign() {
            @Override
            public CreateRegistrationPaymentResponse createPendingRegistrationPayment(
                    CreateRegistrationPaymentRequest request) {
                throw new IllegalStateException(
                        "Payment service unavailable — cannot create a pending "
                                + "registration payment right now. "
                                + cause.getMessage(),
                        cause);
            }
        };
    }
}
