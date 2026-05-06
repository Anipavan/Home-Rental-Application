package com.spa.home_rental_application.user_service.user_service.config;

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
 * springdoc-openapi metadata for the User Service.
 * Declares the {@code bearerAuth} JWT scheme so Swagger UI's "Authorize"
 * button forwards the JWT on every Try-It-Out request.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("Manages user profiles, owner profiles and emergency contacts. " +
                                "Joins to Auth Service (roles) and Property Service (tenants) via Feign.")
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
