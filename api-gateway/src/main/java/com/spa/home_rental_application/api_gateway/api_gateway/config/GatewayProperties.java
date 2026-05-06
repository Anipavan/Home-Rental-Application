package com.spa.home_rental_application.api_gateway.api_gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway-side switches: which paths are public (no JWT required) and the
 * CORS allow-list.
 */
@ConfigurationProperties(prefix = "app.gateway")
@Getter
@Setter
public class GatewayProperties {

    private List<String> publicPaths = new ArrayList<>();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "*";
        private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";
        private String allowedHeaders = "Authorization,Content-Type,Accept,X-Requested-With";
        private long maxAgeSeconds = 3600;
    }
}
