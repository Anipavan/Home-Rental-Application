package com.spa.home_rental_application.user_service.user_service.utils;

import com.spa.home_rental_application.user_service.user_service.utils.events.OwnerRegisteredEvent;
import com.spa.home_rental_application.user_service.user_service.utils.events.UserProfileCreatedEvent;
import com.spa.home_rental_application.user_service.user_service.utils.events.UserProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class userEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.user-topic}")
    private String userTopic;

    public void sendUserProfileCreated(UserProfileCreatedEvent event) {
        log.info("Sending user.created event for userId={}", event.getUserId());
        kafkaTemplate.send(userTopic, event.getUserId(), event);
    }

    public void sendUserProfileUpdated(UserProfileUpdatedEvent event) {
        log.info("Sending user.updated event for userId={}", event.getUserId());
        kafkaTemplate.send(userTopic, event.getUserId(), event);
    }

    public void sendOwnerRegistered(OwnerRegisteredEvent event) {
        log.info("Sending user.created event for ownerId={}", event.getOwnerId());
        kafkaTemplate.send(userTopic, event.getOwnerId(), event);
    }
}
