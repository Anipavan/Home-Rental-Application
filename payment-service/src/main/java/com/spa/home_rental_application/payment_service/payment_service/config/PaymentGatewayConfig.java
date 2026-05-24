package com.spa.home_rental_application.payment_service.payment_service.config;

import com.spa.home_rental_application.payment_service.payment_service.gateway.MockPaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.RazorpayPaymentGateway;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Picks the active {@link PaymentGateway} based on {@code app.payment.gateway}.
 * Switching processors at deploy time is now an env-var change, not a code change.
 *
 * <p>Critical (Audit C13): the mock-gateway bean previously had
 * {@code matchIfMissing = true}. If {@code PAYMENT_GATEWAY} env was unset,
 * typo'd, or blank in prod, the deployment silently fell back to Mock —
 * every "payment" returned MOCK_OK, payment row flipped to PAID, and
 * <em>zero rupees actually collected</em>. No alarm, no log diff, no visible
 * failure. We now require the property to be set explicitly and fail
 * the service start with a clear message otherwise.
 */
@Configuration
@Slf4j
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.payment", name = "gateway", havingValue = "mock")
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

    /**
     * Startup guard: refuse to boot in prod if {@code app.payment.gateway}
     * is unset / blank / something other than mock|razorpay. Without this
     * the service would start cleanly but with NO PaymentGateway bean in
     * the context — payment endpoints would fail with confusing
     * NoSuchBeanDefinitionException at the first call instead of a clear
     * "missing required config" startup error.
     */
    @Configuration
    static class GatewaySelectionValidator {
        @Value("${app.payment.gateway:}")
        private String gatewayName;

        @Autowired
        private Environment env;

        @PostConstruct
        void validate() {
            String[] activeProfiles = env.getActiveProfiles();
            boolean isProd = false;
            for (String p : activeProfiles) {
                if ("prod".equalsIgnoreCase(p)) { isProd = true; break; }
            }
            String g = gatewayName == null ? "" : gatewayName.trim();
            if (g.isEmpty()) {
                String msg = "app.payment.gateway must be set (mock | razorpay). "
                        + "Unsetting it previously fell back to Mock silently — "
                        + "production deployments must declare this explicitly.";
                if (isProd) throw new IllegalStateException(msg);
                log.warn(msg + " Continuing in non-prod profile.");
                return;
            }
            if (!"mock".equalsIgnoreCase(g) && !"razorpay".equalsIgnoreCase(g)) {
                throw new IllegalStateException(
                        "Unsupported app.payment.gateway=" + g + " (expected: mock | razorpay)");
            }
            log.info("PaymentGateway selection validated: app.payment.gateway={}", g);
        }
    }
}
