package com.spa.home_rental_application.auth_commons;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-fast startup guard against the canned placeholder secrets shipped
 * in every service's {@code application.yaml}.
 *
 * <p>The yaml defaults all look like
 * {@code CHANGE_ME_LOCAL_DEV_..._PLACEHOLDER}. They're long enough that
 * Spring + Hibernate + JJWT will accept them during local dev (so the
 * stack still boots without env vars), but obviously bogus. This
 * validator scans the resolved environment at startup and aborts with
 * a loud error if any of those placeholder strings are still in play
 * when the service is running under a profile that should require real
 * secrets (anything other than {@code dev}, {@code local}, {@code test}).
 *
 * <p>The check runs after {@code ApplicationReadyEvent} so the
 * configuration property sources are all resolved but before traffic
 * is served. To bypass it (e.g. for a one-off ops investigation), set
 * {@code app.secrets.bootstrap-check.enabled=false}.
 */
@Slf4j
public class SecretsBootstrapValidator implements ApplicationListener<ApplicationReadyEvent> {

    /** Profiles in which placeholder defaults are acceptable. */
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test", "default");

    /** Property keys that MUST NOT carry a placeholder value in non-dev profiles. */
    private static final List<String> SENSITIVE_KEYS = List.of(
            "spring.datasource.password",
            "app.jwt.secret",
            "app.internal-auth.secret",
            // PII-at-rest encryption key (used by
            // EncryptedStringConverter to protect bank account
            // numbers and similar columns). Production deployment
            // must source this from a secret manager.
            "app.encryption.key",
            "app.razorpay.key-id",
            "app.razorpay.key-secret",
            "app.razorpay.webhook-secret",
            "app.stripe.secret-key",
            "app.stripe.webhook-secret"
    );

    /** Substrings that, if present in a sensitive value, indicate the placeholder default leaked through. */
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "CHANGE_ME",
            "Pavan@123",
            "secretsecret",
            "rzp_test_xxxx",
            "sk_test_xxxx",
            "whsec_xxxx",
            "SuperSecretKeyForJWTTokenGeneration",
            "gateway-shared-secret-change-me-in-prod"
    );

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty("app.secrets.bootstrap-check.enabled", "true"))) {
            log.warn("SecretsBootstrapValidator: explicitly disabled via app.secrets.bootstrap-check.enabled=false");
            return;
        }

        boolean devProfile = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> DEV_PROFILES.contains(p.toLowerCase()))
                || env.getActiveProfiles().length == 0;     // no active profile = dev default

        Map<String, String> offenders = new LinkedHashMap<>();
        for (String key : SENSITIVE_KEYS) {
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) continue;
            for (String marker : PLACEHOLDER_MARKERS) {
                if (value.contains(marker)) {
                    offenders.put(key, marker);
                    break;
                }
            }
        }

        // Audit H9: prod profiles must NOT boot with internal-auth disabled.
        // The flag exists so unit tests can short-circuit the HMAC check,
        // but flipping it in prod leaves every downstream service open
        // to direct hits that bypass the gateway entirely.
        if (!devProfile) {
            String internalAuthEnabled = env.getProperty("app.internal-auth.enabled", "true");
            if ("false".equalsIgnoreCase(internalAuthEnabled)) {
                String msg = "Refusing to start: app.internal-auth.enabled=false in non-dev profile '"
                        + String.join(",", env.getActiveProfiles()) + "'. "
                        + "Disabling gateway-signature verification in prod lets any host on the network "
                        + "talk directly to downstream services. Remove the override, or set "
                        + "SPRING_PROFILES_ACTIVE=dev for local work.";
                log.error(msg);
                event.getApplicationContext().close();
                throw new IllegalStateException(msg);
            }
        }

        if (offenders.isEmpty()) {
            log.info("SecretsBootstrapValidator: all sensitive secrets passed — no placeholder leakage detected.");
            return;
        }

        if (devProfile) {
            log.warn("⚠️  SecretsBootstrapValidator: {} placeholder secret(s) detected in dev profile. " +
                            "This is fine for local dev; rotate before deploying.\n  Offenders: {}",
                    offenders.size(), offenders);
            return;
        }

        // Non-dev profile with placeholder secrets → refuse to serve traffic.
        String detail = offenders.entrySet().stream()
                .map(e -> "  • " + e.getKey() + " contains marker '" + e.getValue() + "'")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String activeProfiles = String.join(",", env.getActiveProfiles());
        String msg = "Refusing to start: placeholder secret(s) detected in non-dev profile '"
                + activeProfiles + "'. Set the corresponding env vars (DB_PASSWORD, JWT_SECRET, "
                + "INTERNAL_AUTH_SECRET, RAZORPAY_*, STRIPE_*) before deploying.\n" + detail;
        log.error(msg);
        // Abort the application context — any in-flight request would be served
        // with insecure defaults.
        ((org.springframework.boot.context.event.ApplicationReadyEvent) event)
                .getApplicationContext().close();
        throw new IllegalStateException(msg);
    }
}
