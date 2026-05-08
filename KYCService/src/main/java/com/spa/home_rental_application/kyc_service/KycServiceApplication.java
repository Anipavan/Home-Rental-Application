package com.spa.home_rental_application.kyc_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;

/**
 * KYC Service entry point.
 * <p>
 * Component scan covers this service plus the shared {@code KafkaEvents}
 * library so the producer beans (KycEventImpl etc.) are wired in.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableRetry
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.kyc_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class KycServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KycServiceApplication.class, args);
    }
}
