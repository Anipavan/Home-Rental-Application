package com.spa.home_rental_application.KafkaEvents.Producers;

import com.spa.home_rental_application.KafkaEvents.Producers.PropertyEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropertyEventImpl implements PropertyEvent {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendPropertyCreated(PropertyCreatedEvent event) {
        kafkaTemplate.send("property-events", event);
    }

    @Override
    public void sendPropertyUpdated(PropertyUpdatedEvent event) {
        kafkaTemplate.send("property-events", event);
    }

    @Override
    public void sendFlatOccupied(FlatOccupiedEvent event) {
        kafkaTemplate.send("property-events", event);
    }

    @Override
    public void sendFlatVacated(FlatVacatedEvent event) {
        kafkaTemplate.send("property-events", event);
    }
}