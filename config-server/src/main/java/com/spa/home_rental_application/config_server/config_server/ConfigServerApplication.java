package com.spa.home_rental_application.config_server.config_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server bootstrap.
 * <p>
 * Serves per-service configuration from {@code classpath:/config/} (the
 * "native" backend). Clients fetch their config by HTTP:
 * <pre>
 *   GET http://localhost:8888/{application}/{profile}
 *   e.g. GET http://localhost:8888/HRA-property-service/default
 * </pre>
 * Registers itself with Eureka so other services can discover it via
 * {@code lb://HRA-config-server}.
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
