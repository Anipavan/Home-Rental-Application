package com.spa.home_rental_application.auth_service.Config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean that the
 * {@link FeignGatewaySigningInterceptor} uses to stamp every outbound
 * Feign call with the same HMAC the API Gateway uses. This makes
 * service-to-service calls (auth → user, etc.) acceptable to the
 * downstream {@code GatewayAuthFilter}, just as if they had come through
 * the gateway.
 * <p>
 * The shared secret comes from {@code app.internal-auth.secret} (auto-bound
 * by the auth-commons auto-config to {@link GatewaySignatureProperties}).
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
