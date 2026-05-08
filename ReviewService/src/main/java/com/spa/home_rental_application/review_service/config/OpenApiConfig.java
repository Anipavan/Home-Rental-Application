package com.spa.home_rental_application.review_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reviewOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RentGenius Review Service")
                .description("Tenant + owner + property reviews on MongoDB")
                .version("v1"));
    }
}
