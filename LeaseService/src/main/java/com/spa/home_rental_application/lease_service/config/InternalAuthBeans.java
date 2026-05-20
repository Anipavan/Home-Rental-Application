package com.spa.home_rental_application.lease_service.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link GatewaySigner} bean for the sibling
 * {@code FeignGatewaySigningInterceptor}. Without it the
 * interceptor can't be created and lease-service's Feign calls
 * (to property-service, compliance-service, etc.) get rejected
 * with {@code 403 GATEWAY_REQUIRED}.
 *
 * <p>Same as auth-service / property-service. Secret comes from
 * {@code app.internal-auth.secret}.
 */
@Configuration
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
