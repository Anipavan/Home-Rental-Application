package com.spa.home_rental_application.maintenance_service.maintenance_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Maintenance Service entry point.
 * Component-scans this service plus the shared KafkaEvents library so the
 * maintenance-event producer beans are discovered.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.maintenance_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class MaintenanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaintenanceServiceApplication.class, args);
    }
}
