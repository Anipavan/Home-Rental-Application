package com.spa.home_rental_application.ServiceRegistry.Service.Registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server bootstrap.
 * <p>
 * Runs on port 8761 by default. Every other service in the platform
 * registers here on startup, and the API Gateway uses {@code lb://}
 * URIs that resolve via this registry.
 * <p>
 * Dashboard UI: http://localhost:8761/
 * Health probe : http://localhost:8761/actuator/health
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
