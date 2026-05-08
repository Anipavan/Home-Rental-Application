package com.spa.home_rental_application.KafkaEvents.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Centralised Kafka topic auto-creation. Any service that depends on the
 * KafkaEvents jar and component-scans this package will, on boot, declare
 * these {@link NewTopic} beans which Spring Kafka submits to {@code KafkaAdmin}
 * for idempotent creation.
 * <p>
 * Topic names come from {@link KafkaTopicProperties} so they remain
 * configurable per environment.
 */
@Configuration
public class KafkaTopicConfig {

    private final KafkaTopicProperties props;

    public KafkaTopicConfig(KafkaTopicProperties props) {
        this.props = props;
    }

    @Bean
    public NewTopic propertyEventsTopic() {
        return TopicBuilder.name(props.getPropertyTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(props.getUserTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(props.getPaymentTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic maintenanceEventsTopic() {
        return TopicBuilder.name(props.getMaintenanceTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(props.getNotificationTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic authEventsTopic() {
        return TopicBuilder.name(props.getAuthTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic kycEventsTopic() {
        return TopicBuilder.name(props.getKycTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic leaseEventsTopic() {
        return TopicBuilder.name(props.getLeaseTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic complianceEventsTopic() {
        return TopicBuilder.name(props.getComplianceTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentEventsTopic() {
        return TopicBuilder.name(props.getDocumentTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reviewEventsTopic() {
        return TopicBuilder.name(props.getReviewTopic()).partitions(3).replicas(1).build();
    }
}
