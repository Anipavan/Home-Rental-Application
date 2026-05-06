package com.spa.home_rental_application.maintenance_service.maintenance_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Activates {@code @CreatedDate} / {@code @LastModifiedDate} on Mongo documents. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
