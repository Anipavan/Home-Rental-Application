package com.spa.home_rental_application.kyc_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kycOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RentGenius KYC Service")
                .description("Aadhaar / PAN / DigiLocker verification with Digio backend")
                .version("v1"));
    }
}
