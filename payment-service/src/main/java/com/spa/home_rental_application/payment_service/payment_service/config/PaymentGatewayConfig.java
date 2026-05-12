package com.spa.home_rental_application.payment_service.payment_service.config;

import com.spa.home_rental_application.payment_service.payment_service.gateway.MockPaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.RazorpayPaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the active {@link PaymentGateway} based on {@code app.payment.gateway}.
 * Switching processors at deploy time is now an env-var change, not a code change.
 */
@Configuration
@Slf4j
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.payment", name = "gateway", havingValue = "mock", matchIfMissing = true)
    public PaymentGateway mockPaymentGateway() {
        log.info("Active payment gateway: mock");
        return new MockPaymentGateway();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.payment", name = "gateway", havingValue = "razorpay")
    public PaymentGateway razorpayPaymentGateway(
            RazorpayProperties props,
            com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository paymentRepository) {
        log.info("Active payment gateway: razorpay (keyId={})", props.getKeyId());
        return new RazorpayPaymentGateway(props, paymentRepository);
    }
}
