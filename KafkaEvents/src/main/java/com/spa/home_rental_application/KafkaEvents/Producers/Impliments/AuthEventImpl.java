package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for Auth Service events.
 * <p>
 * Topic name comes from {@link KafkaTopicProperties#getAuthTopic()}.
 * Message key is {@code authUserId} so events for the same user land on
 * the same partition (in-order delivery per user).
 */
@Service
@Slf4j
public class AuthEventImpl implements AuthServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public AuthEventImpl(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendUserRegistered(UserRegisteredEvent event) {
        log.info("→ {} : user.registered authUserId={} role={}", topics.getAuthTopic(), event.getAuthUserId(), event.getRole());
        kafkaTemplate.send(topics.getAuthTopic(), event.getAuthUserId(), event);
    }

    @Override
    public void sendUserLogin(UserLoginEvent event) {
        log.info("→ {} : user.login authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        kafkaTemplate.send(topics.getAuthTopic(), event.getAuthUserId(), event);
    }

    @Override
    public void sendUserLogout(UserLogoutEvent event) {
        log.info("→ {} : user.logout authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        kafkaTemplate.send(topics.getAuthTopic(), event.getAuthUserId(), event);
    }

    @Override
    public void sendPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("→ {} : user.password.reset.requested authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        kafkaTemplate.send(topics.getAuthTopic(), event.getAuthUserId(), event);
    }
}
