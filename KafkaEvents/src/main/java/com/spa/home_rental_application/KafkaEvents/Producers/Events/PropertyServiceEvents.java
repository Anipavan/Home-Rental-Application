package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.SocietyBankAccountSavedEvent;
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

    /**
     * Fired when a maintainer/owner saves or updates the SOCIETY's
     * bank + KYC details. payment-service consumes this and registers
     * (or PATCHes) the Cashfree Easy Split vendor keyed on the
     * society so maintenance payments settle to the society's own
     * account instead of the maintainer's personal one.
     */
    void sendSocietyBankAccountSaved(SocietyBankAccountSavedEvent event);
}
