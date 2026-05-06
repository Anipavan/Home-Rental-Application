package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Concrete producer for property/flat lifecycle events.
 * <p>
 * Topic name is sourced from {@link KafkaTopicProperties#getPropertyTopic()}
 * so it is configurable per environment (no hardcoded strings).
 * The Kafka message key is the {@code propertyId}/{@code flatId} so events
 * for the same aggregate are routed to the same partition (ordering guarantee).
 */
@Service
@Slf4j
public class PropertyEventImpl implements PropertyServiceEvents {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topics;

    public PropertyEventImpl(KafkaTemplate<String, Object> kafkaTemplate,
                             KafkaTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void sendPropertyCreated(PropertyCreatedEvent event) {
        log.debug("→ {} : property.created propertyId={}", topics.getPropertyTopic(), event.getPropertyId());
        kafkaTemplate.send(topics.getPropertyTopic(), event.getPropertyId(), event);
    }

    @Override
    public void sendPropertyUpdated(PropertyUpdatedEvent event) {
        log.debug("→ {} : property.updated propertyId={}", topics.getPropertyTopic(), event.getPropertyId());
        kafkaTemplate.send(topics.getPropertyTopic(), event.getPropertyId(), event);
    }

    @Override
    public void sendFlatOccupied(FlatOccupiedEvent event) {
        log.debug("→ {} : flat.occupied flatId={}", topics.getPropertyTopic(), event.getFlatId());
        kafkaTemplate.send(topics.getPropertyTopic(), event.getFlatId(), event);
    }

    @Override
    public void sendFlatVacated(FlatVacatedEvent event) {
        log.debug("→ {} : flat.vacated flatId={}", topics.getPropertyTopic(), event.getFlatId());
        kafkaTemplate.send(topics.getPropertyTopic(), event.getFlatId(), event);
    }
}
