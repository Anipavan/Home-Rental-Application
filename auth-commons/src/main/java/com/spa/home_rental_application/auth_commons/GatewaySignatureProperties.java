package com.spa.home_rental_application.auth_commons;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the gateway-signature trust boundary.
 *
 * <pre>
 * app:
 *   internal-auth:
 *     enabled: true
 *     secret: change-me-base64-encoded-256-bit
 *     allowed-clock-skew-seconds: 60
 *     public-paths:
 *       - /actuator/**
 *       - /v3/api-docs/**
 *       - /swagger-ui/**
 *       - /swagger-ui.html
 * </pre>
 *
 * Every downstream service binds this. The {@link GatewayAuthFilter} uses
 * {@link #getSecret()} to verify the HMAC the API Gateway adds to every
 * proxied request.
 */
@ConfigurationProperties(prefix = "app.internal-auth")
@Getter
@Setter
public class GatewaySignatureProperties {

    /** Master switch. Useful for local dev or unit tests. */
    private boolean enabled = true;

    /**
     * Shared HMAC-SHA256 secret. MUST match {@code app.internal-auth.secret}
     * on the API Gateway. Override in prod via env var or Vault.
     */
    private String secret;

    /** Max allowed difference between the gateway's timestamp and the local clock (seconds). */
    private long allowedClockSkewSeconds = 60;

    /**
     * URI patterns that bypass the gateway-signature check. Useful for
     * /actuator/health probes from the container orchestrator and Swagger
     * docs which are typically scraped locally.
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    ));
}
