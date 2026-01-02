package com.spa.home_rental_application.KafkaEvents.Producers;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyUpdatedEvent;

public interface PropertyEvent {
    void sendPropertyCreated(PropertyCreatedEvent event);
    void sendPropertyUpdated(PropertyUpdatedEvent event);
    void sendFlatOccupied(FlatOccupiedEvent event);
    void sendFlatVacated(FlatVacatedEvent event);

}
