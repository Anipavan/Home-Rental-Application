package com.spa.home_rental_application.user_service.user_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean used by
 * {@link FeignGatewaySigningInterceptor} to stamp every outbound Feign
 * call with the same HMAC the API Gateway uses, so the downstream
 * {@code GatewayAuthFilter} accepts service-to-service calls.
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
