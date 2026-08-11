package com.spa.home_rental_application.payment_service.payment_service.config;

import com.spa.home_rental_application.payment_service.payment_service.gateway.CashfreePaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.MockPaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
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
 * Switching processors is an env-var change, not a code change.
 *
 * <p>Supported values today: {@code mock} (dev + tests) and — once Phase 3 of
 * the Cashfree Easy Split integration lands — {@code cashfree}. The old
 * {@code razorpay} value was removed alongside the Route scaffolding; the
 * gateway strategy pattern is deliberately preserved so slotting Cashfree in
 * is a single new bean, not a rewrite.
 *
 * <p>Critical (Audit C13): the mock-gateway bean previously had
 * {@code matchIfMissing = true}. If {@code APP_PAYMENT_GATEWAY} env was unset,
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
    @ConditionalOnProperty(prefix = "app.payment", name = "gateway", havingValue = "cashfree")
    public PaymentGateway cashfreePaymentGateway(
            CashfreeProperties cashfreeProps,
            com.spa.home_rental_application.payment_service.payment_service.service.VendorUsageRecorder usageRecorder) {
        log.info("Active payment gateway: cashfree (env={})", cashfreeProps.getEnvironment());
        return new CashfreePaymentGateway(cashfreeProps, usageRecorder);
    }

    /**
     * Startup guard: refuse to boot in prod if {@code app.payment.gateway}
     * is unset / blank / something other than the supported set. Without this
     * the service would start cleanly but with NO PaymentGateway bean in
     * the context — payment endpoints would fail with confusing
     * NoSuchBeanDefinitionException at the first call instead of a clear
     * "missing required config" startup error.
     */
    @Configuration
    static class GatewaySelectionValidator {
        /** Values app.payment.gateway may legally take. Kept as a single
         *  source of truth so the error message stays in sync with the
         *  set of gateway beans this config actually knows how to wire. */
        private static final java.util.Set<String> SUPPORTED =
                java.util.Set.of("mock", "cashfree");

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
            String g = gatewayName == null ? "" : gatewayName.trim().toLowerCase();
            if (g.isEmpty()) {
                String msg = "app.payment.gateway must be set (" + SUPPORTED + "). "
                        + "Unsetting it previously fell back to Mock silently — "
                        + "production deployments must declare this explicitly.";
                if (isProd) throw new IllegalStateException(msg);
                log.warn(msg + " Continuing in non-prod profile.");
                return;
            }
            if (!SUPPORTED.contains(g)) {
                throw new IllegalStateException(
                        "Unsupported app.payment.gateway=" + g
                        + " (expected one of: " + SUPPORTED + ")");
            }
            log.info("PaymentGateway selection validated: app.payment.gateway={}", g);
        }
    }
}
