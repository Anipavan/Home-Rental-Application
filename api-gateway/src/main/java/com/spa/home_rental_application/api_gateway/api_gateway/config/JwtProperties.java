package com.spa.home_rental_application.api_gateway.api_gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT validation config — secret + expected issuer. Must match the values
 * used by Auth Service to sign access tokens.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private String issuer = "home-rental-auth";
}
