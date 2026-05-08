package com.spa.home_rental_application.KafkaEvents.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for Kafka topic names across the platform.
 * <p>
 * Bound from {@code app.kafka.*} in each consumer's application.yaml. Lives
 * in the KafkaEvents shared library so every service that depends on this
 * jar (and component-scans this package) gets the same defaults.
 *
 * <pre>
 * app:
 *   kafka:
 *     property-topic: property-events
 *     user-topic: user-events
 *     payment-topic: payment-events
 *     maintenance-topic: maintenance-events
 *     notification-topic: notification-events
 *     auth-topic: auth-events
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.kafka")
@Getter
@Setter
public class KafkaTopicProperties {
    private String propertyTopic = "property-events";
    private String userTopic = "user-events";
    private String paymentTopic = "payment-events";
    private String maintenanceTopic = "maintenance-events";
    private String notificationTopic = "notification-events";
    private String authTopic = "auth-events";
    private String kycTopic = "kyc-events";
    private String leaseTopic = "lease-events";
    private String complianceTopic = "compliance-events";
    private String documentTopic = "document-events";
    private String reviewTopic = "review-events";
}
