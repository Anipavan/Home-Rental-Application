package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.TenantVacateScheduledEvent;

public interface PropertyServiceEvents {
    void sendPropertyCreated(PropertyCreatedEvent event);
    void sendPropertyUpdated(PropertyUpdatedEvent event);
    void sendFlatOccupied(FlatOccupiedEvent event);
    void sendFlatVacated(FlatVacatedEvent event);

    /**
     * Fired by VacateScheduler 10 days before a tenant's scheduled
     * vacate. notification-service's PropertyEventListener consumes
     * this and fans an owner-facing alert across every channel.
     */
    void sendTenantVacateScheduled(TenantVacateScheduledEvent event);
}
