package com.spa.home_rental_application.auth_service.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for {@code app.jwt.*} properties — secret, TTLs,
 * and issuer claim. Lifts JWT secrets and validity windows out of constants
 * so prod can override via environment variables / Vault.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    /** Base64-encoded HMAC-SHA256 secret. ≥ 256 bits required. */
    private String secret;
    private long accessTokenValiditySeconds = 3600;
    private long refreshTokenValiditySeconds = 30L * 24 * 3600;
    private String issuer = "home-rental-auth";
}
