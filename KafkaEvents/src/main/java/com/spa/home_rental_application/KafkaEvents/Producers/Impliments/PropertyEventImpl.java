package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropertyEventImpl implements PropertyServiceEvents {

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