package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.EmailVerificationRequestedEvent;
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
 *
 * <p><b>Outage safety:</b> every publish is wrapped in {@link #publishSafely}
 * so a Kafka broker outage NEVER fails the originating request. The login
 * / register / logout / forgot-password flows have all been bitten by this
 * historically — Kafka goes down, the synchronous {@code send()} call
 * either hangs or throws inside the request thread, and the user sees a
 * 500. The fix is to treat these events as fire-and-forget: log the
 * failure, drop the event, return success to the caller. The downstream
 * consumers (audit-service, notification-service) lose a single event on
 * a broker outage, which is acceptable for the event types here — none
 * of them is on a critical money path. (For events that DO need
 * at-least-once delivery, the right pattern is a transactional outbox
 * table, but we're nowhere near needing that today.)
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
        publishSafely("user.registered", event.getAuthUserId(), event);
    }

    @Override
    public void sendUserLogin(UserLoginEvent event) {
        log.info("→ {} : user.login authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        publishSafely("user.login", event.getAuthUserId(), event);
    }

    @Override
    public void sendUserLogout(UserLogoutEvent event) {
        log.info("→ {} : user.logout authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        publishSafely("user.logout", event.getAuthUserId(), event);
    }

    @Override
    public void sendPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("→ {} : user.password.reset.requested authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        publishSafely("user.password.reset.requested", event.getAuthUserId(), event);
    }

    @Override
    public void sendEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        log.info("→ {} : user.email.verification.requested authUserId={}", topics.getAuthTopic(), event.getAuthUserId());
        publishSafely("user.email.verification.requested", event.getAuthUserId(), event);
    }

    /**
     * Publish with belt-and-braces failure isolation:
     *   1. {@code kafkaTemplate.send} returns a CompletableFuture; we
     *      attach an error callback so async send failures (broker
     *      dropped the connection mid-flight) get logged and swallowed.
     *   2. The {@code send} call itself can throw synchronously if the
     *      producer's metadata fetch blocks past {@code max.block.ms}
     *      — the surrounding try/catch handles that path.
     *
     * Either way, the caller (login / register / logout flow) sees a
     * normal return and the request continues.
     */
    private void publishSafely(String eventName, String key, Object event) {
        try {
            kafkaTemplate.send(topics.getAuthTopic(), key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka publish failed for {} authUserId={} — dropping event. cause={}",
                                    eventName, key, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Kafka publish threw synchronously for {} authUserId={} — dropping event. cause={}",
                    eventName, key, e.getMessage());
        }
    }
}
