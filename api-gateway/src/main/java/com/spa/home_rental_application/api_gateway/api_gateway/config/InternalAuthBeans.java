package com.spa.home_rental_application.api_gateway.api_gateway.config;

import com.spa.home_rental_application.auth_commons.GatewaySignatureProperties;
import com.spa.home_rental_application.auth_commons.GatewaySigner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link GatewaySignatureProperties} (from auth-commons) and
 * exposes a {@link GatewaySigner} bean used by the signing global filter
 * to stamp every proxied request.
 */
@Configuration
@EnableConfigurationProperties(GatewaySignatureProperties.class)
public class InternalAuthBeans {

    @Bean
    public GatewaySigner gatewaySigner(GatewaySignatureProperties props) {
        return new GatewaySigner(props.getSecret(), props.getAllowedClockSkewSeconds());
    }
}
