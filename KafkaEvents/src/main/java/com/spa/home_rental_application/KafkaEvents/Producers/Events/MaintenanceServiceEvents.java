package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.*;

/**
 * Producer interface for maintenance-request lifecycle events.
 * Implemented by {@code MaintenanceEventImpl}.
 */
public interface MaintenanceServiceEvents {
    void sendMaintenanceCreated(MaintenanceCreatedEvent event);
    void sendMaintenanceAssigned(MaintenanceAssignedEvent event);
    void sendMaintenanceStatusChanged(MaintenanceStatusChangedEvent event);
    void sendMaintenanceResolved(MaintenanceResolvedEvent event);
    void sendMaintenanceCommentAdded(MaintenanceCommentAddedEvent event);
}
