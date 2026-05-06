package com.spa.home_rental_application.property_service.property_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi metadata for the Property Service.
 * Swagger UI at /swagger-ui.html and JSON spec at /v3/api-docs.
 *
 * Declares the {@code bearerAuth} JWT scheme so the Swagger UI's
 * "Authorize" button accepts a token issued by Auth Service. Once entered
 * the same token is sent on every Try-It-Out request.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI propertyServiceOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Property Service API")
                        .description("Manages buildings, flats, occupancy, and property images for the Home Rental Application.")
                        .version("v1")
                        .contact(new Contact().name("Home Rental Platform Team").email("platform@homerental.local"))
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
