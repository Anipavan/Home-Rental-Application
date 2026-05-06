package com.spa.home_rental_application.api_gateway.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.asList(props.getCors().getAllowedOrigins().split(",")));
        cfg.setAllowedMethods(Arrays.asList(props.getCors().getAllowedMethods().split(",")));
        cfg.setAllowedHeaders(Arrays.asList(props.getCors().getAllowedHeaders().split(",")));
        cfg.setExposedHeaders(java.util.List.of(
                "X-Token-Expired", "X-Token-Invalid", "X-Auth-Required"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(props.getCors().getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        src.registerCorsConfiguration("/**", cfg);
        return new CorsWebFilter(src);
    }
}
