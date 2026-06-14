package com.spa.home_rental_application.api_gateway.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    /**
     * Vendor-controlled origins that MUST be in the CORS allowlist for
     * the Razorpay payment flow to work. Baked into the source as
     * defence-in-depth so a future deploy can't silently regress by
     * shipping with these missing from {@code CORS_ALLOWED_ORIGINS}.
     *
     * <p>Required because Razorpay's hosted checkout (and the test-mode
     * mocksharp bank) does a cross-origin {@code POST} / {@code GET}
     * back to {@code /rentals/v1/payments/razorpay-return/{id}} when
     * the user clicks "Success". The browser sends
     * {@code Origin: https://api.razorpay.com} (or
     * {@code checkout.razorpay.com} in live mode). The global
     * {@code CorsWebFilter} registered below validates the Origin
     * header against the allowed list <em>before</em> the route
     * filters fire — without these entries the gateway 403s the
     * redirect and the user sees Chrome's "Access denied" page instead
     * of our {@code /app/payments/{id}/return} SPA route.
     *
     * <p>Symptom of regression: payment succeeds in Razorpay's records
     * (charges go through, refunds work), but our SPA never confirms
     * it to the user. Hardcoding here means an env-var typo can't
     * break the entire payment UX.
     */
    private static final List<String> RAZORPAY_ORIGINS = List.of(
            "https://api.razorpay.com",        // test-mode mocksharp bank
            "https://checkout.razorpay.com",   // live hosted-checkout SDK
            "https://lumberjack.razorpay.com"  // Razorpay analytics beacon
    );

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties props) {
        // Merge env-configured origins with the always-on Razorpay
        // list. LinkedHashSet preserves order and drops accidental
        // duplicates (someone adding Razorpay to .env too is harmless).
        Set<String> origins = new LinkedHashSet<>();
        for (String o : props.getCors().getAllowedOrigins().split(",")) {
            String trimmed = o.trim();
            if (!trimmed.isEmpty()) origins.add(trimmed);
        }
        origins.addAll(RAZORPAY_ORIGINS);

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(new ArrayList<>(origins));
        cfg.setAllowedMethods(Arrays.asList(props.getCors().getAllowedMethods().split(",")));
        cfg.setAllowedHeaders(Arrays.asList(props.getCors().getAllowedHeaders().split(",")));
        cfg.setExposedHeaders(List.of(
                "X-Token-Expired", "X-Token-Invalid", "X-Auth-Required"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(props.getCors().getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        src.registerCorsConfiguration("/**", cfg);
        return new CorsWebFilter(src);
    }
}
