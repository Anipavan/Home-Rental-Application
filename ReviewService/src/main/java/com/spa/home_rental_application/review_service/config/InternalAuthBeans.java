package com.spa.home_rental_application.review_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean for the sibling
 * {@code FeignGatewaySigningInterceptor}. Without this, ReviewService's
 * Feign calls to property-service (used to resolve a building's
 * ownerId so the "your tenant left a review" email reaches the owner)
 * get rejected with {@code 403 GATEWAY_REQUIRED}.
 *
 * <p>Ported from auth-service / property-service / payment-service /
 * lease-service. Secret comes from {@code app.internal-auth.secret}
 * (env var INTERNAL_AUTH_SECRET — same across the platform so rotating
 * the key is a single change in {@code /opt/anirudhhomes/.env}).
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
