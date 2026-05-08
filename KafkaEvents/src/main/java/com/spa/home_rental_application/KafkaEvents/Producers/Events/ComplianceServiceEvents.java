package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.GstInvoiceGeneratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.ReraRegisteredEvent;

/**
 * Producer contract for Compliance Service domain events
 * (RERA registrations, GST invoice generation).
 */
public interface ComplianceServiceEvents {
    void sendReraRegistered(ReraRegisteredEvent event);
    void sendGstInvoiceGenerated(GstInvoiceGeneratedEvent event);
}
