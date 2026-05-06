package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for user/owner profile events.
 * <p>
 * Topic name comes from {@link KafkaTopicProperties#getUserTopic()}; the
 * Kafka message key is the user/owner id so events for the same user land
 * on the same partition (in-order delivery per user).
 */
@Service
@Slf4j
public class UserServiceEventsImpul implements UserServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public UserServiceEventsImpul(KafkaTemplate<String, Object> kafkaTemplate,
                                  KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendUserProfileCreated(UserProfileCreatedEvent event) {
        log.info("→ {} : user.profile.created userId={}", topics.getUserTopic(), event.getUserId());
        kafkaTemplate.send(topics.getUserTopic(), event.getUserId(), event);
    }

    @Override
    public void sendUserProfileUpdated(UserProfileUpdatedEvent event) {
        log.info("→ {} : user.profile.updated userId={}", topics.getUserTopic(), event.getUserId());
        kafkaTemplate.send(topics.getUserTopic(), event.getUserId(), event);
    }

    @Override
    public void sendOwnerRegistered(OwnerRegisteredEvent event) {
        log.info("→ {} : owner.registered ownerId={}", topics.getUserTopic(), event.getOwnerId());
        kafkaTemplate.send(topics.getUserTopic(), event.getOwnerId(), event);
    }
}
