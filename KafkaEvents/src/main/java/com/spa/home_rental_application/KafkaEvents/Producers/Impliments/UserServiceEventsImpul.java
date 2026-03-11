package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


@Slf4j
@Component
@RequiredArgsConstructor
@Service
public class UserServiceEventsImpul implements UserServiceEvents {

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