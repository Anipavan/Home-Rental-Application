package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycFailedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycPanVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents.KycVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.KycServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for KYC events.
 * <p>
 * Topic name resolved from {@link KafkaTopicProperties#getKycTopic()}; the
 * Kafka message key is the userId so all events for the same user land on
 * the same partition (in-order delivery per user).
 */
@Service
@Slf4j
public class KycEventImpl implements KycServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public KycEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                        KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendKycVerified(KycVerifiedEvent event) {
        log.info("→ {} : kyc.verified userId={} provider={}",
                topics.getKycTopic(), event.getUserId(), event.getKycProvider());
        kafkaTemplate.send(topics.getKycTopic(), event.getUserId(), event);
    }

    @Override
    public void sendKycFailed(KycFailedEvent event) {
        log.warn("→ {} : kyc.failed userId={} reason={}",
                topics.getKycTopic(), event.getUserId(), event.getFailureCode());
        kafkaTemplate.send(topics.getKycTopic(), event.getUserId(), event);
    }

    @Override
    public void sendKycPanVerified(KycPanVerifiedEvent event) {
        log.info("→ {} : kyc.pan.verified userId={}",
                topics.getKycTopic(), event.getUserId());
        kafkaTemplate.send(topics.getKycTopic(), event.getUserId(), event);
    }
}
