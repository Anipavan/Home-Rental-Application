package com.spa.home_rental_application.document_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers="
})
class DocumentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
