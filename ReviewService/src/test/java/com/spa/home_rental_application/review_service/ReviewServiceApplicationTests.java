package com.spa.home_rental_application.review_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.data.mongodb.uri=mongodb://localhost:27017/test"
})
class ReviewServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
