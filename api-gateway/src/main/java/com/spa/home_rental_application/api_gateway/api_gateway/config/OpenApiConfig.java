package com.spa.home_rental_application.api_gateway.api_gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Aggregates Swagger UI for every Home-Rental microservice behind a single
 * gateway URL: <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a>.
 *
 * <p>The actual service-spec dropdown is configured in
 * {@code application.yaml} under {@code springdoc.swagger-ui.urls} — this
 * bean only supplies the gateway-level metadata + the {@code bearerAuth}
 * security scheme so the "Authorize" button accepts a JWT once and reuses
 * it across every service definition.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Home Rental Application — Unified API")
                        .description("Single entry-point Swagger UI for every microservice in the platform. " +
                                "Pick a service from the \"Definition\" dropdown (top-right). " +
                                "Click \"Authorize\" once with your JWT to apply it across every service.")
                        .version("v1")
                        .contact(new Contact().name("Home Rental Platform Team").email("platform@homerental.local"))
                        .license(new License().name("Apache 2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local gateway")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
