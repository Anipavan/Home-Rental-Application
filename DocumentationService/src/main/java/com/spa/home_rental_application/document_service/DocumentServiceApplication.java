package com.spa.home_rental_application.document_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Document Service entry point. Component scan covers this service plus the
 * shared {@code KafkaEvents} library so producer beans (DocumentEventImpl)
 * are wired in.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@ConfigurationPropertiesScan
@ComponentScan({
        "com.spa.home_rental_application.document_service",
        "com.spa.home_rental_application.KafkaEvents"
})
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
