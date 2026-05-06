package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReminderEvent {
    private String eventType;       // "payment.reminder"
    private String paymentId;
    private String tenantId;
    private String reminderType;    // EMAIL, SMS, PUSH
    private long daysUntilDue;
    private Instant timestamp;
}
