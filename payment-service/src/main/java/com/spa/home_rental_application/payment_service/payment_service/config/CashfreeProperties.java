package com.spa.home_rental_application.payment_service.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cashfree Easy Split credentials + environment selector.
 *
 * <p>Sourced from the {@code app.cashfree.*} block in application.yaml,
 * which pulls the real values from {@code CASHFREE_APP_ID},
 * {@code CASHFREE_SECRET_KEY}, {@code CASHFREE_WEBHOOK_SECRET}, and
 * {@code CASHFREE_ENV} env vars in prod.
 *
 * <p>The placeholder defaults ({@code CHANGE_ME_CASHFREE_*}) keep the
 * service booting cleanly in dev while flagging the missing secrets to
 * {@link com.spa.home_rental_application.auth_commons.SecretsBootstrapValidator}
 * on prod profiles.
 */
@ConfigurationProperties(prefix = "app.cashfree")
@Getter
@Setter
public class CashfreeProperties {

    /** Cashfree App ID (test keys start with {@code TEST}, prod keys don't). */
    private String appId;

    /** Cashfree Secret Key. */
    private String secretKey;

    /**
     * Webhook signature secret — set separately in Dashboard → Developers
     * → Webhooks. Used to verify {@code x-webhook-signature} on inbound
     * webhook calls. Nullable in dev.
     */
    private String webhookSecret;

    /**
     * {@code sandbox} (default, uses https://sandbox.cashfree.com/pg) or
     * {@code production} (uses https://api.cashfree.com/pg). The gateway
     * picks the base URL off this value at construction time.
     */
    private String environment = "sandbox";

    /**
     * Pinned Cashfree API version. Bump when we deliberately migrate to
     * a newer schema — Cashfree accepts multiple versions concurrently.
     */
    private String apiVersion = "2023-08-01";

    /** Base URL for the active environment. */
    public String baseUrl() {
        if ("production".equalsIgnoreCase(environment)) {
            return "https://api.cashfree.com/pg";
        }
        return "https://sandbox.cashfree.com/pg";
    }

    /** True when neither key is the placeholder default. */
    public boolean credentialsConfigured() {
        return notPlaceholder(appId) && notPlaceholder(secretKey);
    }

    private static boolean notPlaceholder(String s) {
        return s != null
                && !s.isBlank()
                && !s.toUpperCase().contains("CHANGE_ME");
    }
}
