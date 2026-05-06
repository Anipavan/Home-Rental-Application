package com.spa.home_rental_application.KafkaEvents.Producers.Impliments;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.config.KafkaTopicProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PropertyEventImplTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    private PropertyEventImpl impl() {
        KafkaTopicProperties t = new KafkaTopicProperties();
        return new PropertyEventImpl(kafkaTemplate, t);
    }

    @Test
    void sendPropertyCreated_usesPropertyTopicAndIdAsKey() {
        PropertyCreatedEvent e = PropertyCreatedEvent.builder()
                .eventType("property.created").propertyId("BLD-1").ownerId("O1")
                .timestamp(Instant.now()).build();
        impl().sendPropertyCreated(e);
        verify(kafkaTemplate).send("property-events", "BLD-1", e);
    }

    @Test
    void sendPropertyUpdated_usesPropertyTopicAndIdAsKey() {
        PropertyUpdatedEvent e = PropertyUpdatedEvent.builder()
                .eventType("property.updated").propertyId("BLD-1").ownerId("O1")
                .timestamp(Instant.now()).build();
        impl().sendPropertyUpdated(e);
        verify(kafkaTemplate).send("property-events", "BLD-1", e);
    }

    @Test
    void sendFlatOccupied_usesFlatIdAsKey() {
        FlatOccupiedEvent e = FlatOccupiedEvent.builder()
                .eventType("flat.occupied").flatId("FLT-1").tenantId("USR-1")
                .timestamp(Instant.now()).build();
        impl().sendFlatOccupied(e);
        verify(kafkaTemplate).send("property-events", "FLT-1", e);
    }

    @Test
    void sendFlatVacated_usesFlatIdAsKey() {
        FlatVacatedEvent e = FlatVacatedEvent.builder()
                .eventType("flat.vacated").flatId("FLT-1").tenantId("USR-1")
                .timestamp(Instant.now()).build();
        impl().sendFlatVacated(e);
        verify(kafkaTemplate).send("property-events", "FLT-1", e);
    }
}
