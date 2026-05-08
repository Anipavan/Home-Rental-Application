package com.spa.home_rental_application.lease_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lease Service entry point. Component scan covers this service plus the
 * shared {@code KafkaEvents} library so the producer beans (LeaseEventImpl)
 * are wired in. {@code @EnableScheduling} powers the daily expiry cron.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableRetry
@EnableScheduling
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.lease_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class LeaseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaseServiceApplication.class, args);
    }
}
