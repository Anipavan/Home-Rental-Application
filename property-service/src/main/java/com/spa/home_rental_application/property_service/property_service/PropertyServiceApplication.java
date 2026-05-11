package com.spa.home_rental_application.property_service.property_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Property Service entry point.
 * <p>
 * The component scan covers this service plus the shared KafkaEvents library
 * so producer beans (PropertyEventImpl, etc.) are picked up. Cross-service
 * scans of auth_service have been removed — no microservice should reach
 * into another service's package at runtime.
 *
 * <p>{@code @EnableScheduling} drives the saved-search matcher
 * ({@code SavedSearchMatcherScheduler}); without it the tenant alert
 * fan-out would never fire.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableFeignClients
@EnableScheduling
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.property_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class PropertyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
