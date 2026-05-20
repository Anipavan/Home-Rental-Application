package com.spa.home_rental_application.property_service.property_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean that the sibling
 * {@link FeignGatewaySigningInterceptor} uses to stamp every outbound
 * Feign call with the same HMAC the API Gateway uses. Without this
 * bean, property-service's Feign calls to user-service get rejected by
 * the downstream {@code GatewayAuthFilter} with
 * {@code 403 GATEWAY_REQUIRED}.
 *
 * <p>Ported from auth-service's identical config. The shared secret
 * comes from {@code app.internal-auth.secret} (auto-bound by the
 * auth-commons auto-config to {@link GatewaySignatureProperties}). Same
 * env var across the platform — INTERNAL_AUTH_SECRET — so rotating the
 * key is a single change in {@code /opt/anirudhhomes/.env} + restart.
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
