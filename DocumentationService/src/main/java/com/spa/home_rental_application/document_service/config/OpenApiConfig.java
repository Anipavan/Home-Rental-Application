package com.spa.home_rental_application.document_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RentGenius Document Service")
                .description("Secure upload + OCR + pre-signed downloads")
                .version("v1"));
    }
}
