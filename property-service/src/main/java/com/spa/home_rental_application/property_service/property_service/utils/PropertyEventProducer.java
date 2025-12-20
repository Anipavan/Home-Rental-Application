package com.spa.home_rental_application.property_service.property_service.utils;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.FlatVacatedEvent;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.property_service.property_service.utils.kafkaEvents.PropertyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.property-topic}")
    private String propertyTopic;

    public void sendPropertyCreated(PropertyCreatedEvent event) {
        log.info("Sending property.created event for propertyId={}", event.getPropertyId());
        kafkaTemplate.send(propertyTopic, event.getPropertyId(), event);
    }

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
