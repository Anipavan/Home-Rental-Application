package com.spa.home_rental_application.compliance_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI complianceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RentGenius Compliance Service")
                .description("RERA registration + GST invoicing for India")
                .version("v1"));
    }
}
