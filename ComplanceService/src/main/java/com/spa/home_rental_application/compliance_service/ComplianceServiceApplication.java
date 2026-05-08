package com.spa.home_rental_application.compliance_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Compliance Service entry point.
 * <p>
 * Component scan covers this service plus the shared {@code KafkaEvents}
 * library so the producer beans (ComplianceEventImpl etc.) are wired in.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableRetry
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.compliance_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class ComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceServiceApplication.class, args);
    }
}
