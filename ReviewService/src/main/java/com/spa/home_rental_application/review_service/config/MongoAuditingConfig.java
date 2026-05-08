package com.spa.home_rental_application.review_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Powers @CreatedDate / @LastModifiedDate on Review entity. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
