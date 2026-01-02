package com.spa.home_rental_application.property_service.property_service.utils.events.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.PropertyEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PropertyEventImpl implements PropertyEvent {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.property-topic:property-events}")
    private String propertyTopic;

    @Override
    public void sendPropertyCreated(PropertyCreatedEvent event) {
        log.info("Sending property.created event for propertyId={}", event.getPropertyId());
        kafkaTemplate.send(propertyTopic, event.getPropertyId(), event);
    }

    @Override
    public void sendPropertyUpdated(PropertyUpdatedEvent event) {
        log.info("Sending property.updated event for propertyId={}", event.getPropertyId());
        kafkaTemplate.send(propertyTopic, event.getPropertyId(), event);
    }

    public void sendFlatOccupied(FlatOccupiedEvent event) {
        log.info("Sending flat.occupied event for flatId={}", event.getFlatId());
        kafkaTemplate.send(propertyTopic, event.getFlatId(), event);
    }

    public void sendFlatVacated(FlatVacatedEvent event) {
        log.info("Sending flat.vacated event for flatId={}", event.getFlatId());
        kafkaTemplate.send(propertyTopic, event.getFlatId(), event);
    }
}
