package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;

/**
 * Producer interface for rent payment lifecycle events.
 * Implemented by {@code PaymentEventImpl}.
 */
public interface PaymentServiceEvents {
    void sendPaymentCreated(PaymentCreatedEvent event);
    void sendPaymentCompleted(PaymentCompletedEvent event);
    void sendPaymentFailed(PaymentFailedEvent event);
    void sendPaymentOverdue(PaymentOverdueEvent event);
    void sendPaymentReminder(PaymentReminderEvent event);
}
