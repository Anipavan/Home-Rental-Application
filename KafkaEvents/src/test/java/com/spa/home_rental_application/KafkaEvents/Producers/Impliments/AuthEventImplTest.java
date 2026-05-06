package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventImplTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    private AuthEventImpl impl() {
        return new AuthEventImpl(kafkaTemplate, new KafkaTopicProperties());
    }

    @Test
    void userRegistered_usesAuthTopicAndAuthUserIdAsKey() {
        UserRegisteredEvent e = UserRegisteredEvent.builder()
                .eventType("user.registered").authUserId("123")
                .userName("alice").role("TENANT").timestamp(Instant.now()).build();
        impl().sendUserRegistered(e);
        verify(kafkaTemplate).send("auth-events", "123", e);
    }

    @Test
    void userLogin_usesAuthTopicAndAuthUserIdAsKey() {
        UserLoginEvent e = UserLoginEvent.builder()
                .eventType("user.login").authUserId("123").build();
        impl().sendUserLogin(e);
        verify(kafkaTemplate).send("auth-events", "123", e);
    }

    @Test
    void userLogout_usesAuthTopicAndAuthUserIdAsKey() {
        UserLogoutEvent e = UserLogoutEvent.builder()
                .eventType("user.logout").authUserId("123").build();
        impl().sendUserLogout(e);
        verify(kafkaTemplate).send("auth-events", "123", e);
    }
}
