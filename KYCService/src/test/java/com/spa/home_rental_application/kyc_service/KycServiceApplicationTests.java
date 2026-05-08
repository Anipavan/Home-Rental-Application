package com.spa.home_rental_application.kyc_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers="
})
class KycServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
