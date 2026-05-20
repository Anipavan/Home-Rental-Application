package com.spa.home_rental_application.payment_service.payment_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean used by the sibling
 * {@code FeignGatewaySigningInterceptor} to HMAC-sign outbound
 * Feign calls. Without this bean the interceptor's
 * {@code @Bean} method fails to resolve and Spring's
 * {@code DefaultListableBeanFactory} silently drops the
 * interceptor — service-to-service calls then hit downstream
 * {@code GatewayAuthFilter} with no signing headers and get
 * rejected as {@code 403 GATEWAY_REQUIRED}.
 *
 * <p>Same pattern as auth-service. Secret comes from
 * {@code app.internal-auth.secret} (env var INTERNAL_AUTH_SECRET).
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
