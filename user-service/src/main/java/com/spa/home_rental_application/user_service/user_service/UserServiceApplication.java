package com.spa.home_rental_application.user_service.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * User Service entry point.
 * <p>
 * The component scan covers this service plus the shared KafkaEvents library
 * so the producer beans (UserServiceEventsImpul, etc.) are wired in. We do
 * NOT scan other services' packages.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.user_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
