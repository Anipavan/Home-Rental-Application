package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;

public interface PropertyServiceEvents {
    void sendPropertyCreated(PropertyCreatedEvent event);
    void sendPropertyUpdated(PropertyUpdatedEvent event);
    void sendFlatOccupied(FlatOccupiedEvent event);
    void sendFlatVacated(FlatVacatedEvent event);

}
