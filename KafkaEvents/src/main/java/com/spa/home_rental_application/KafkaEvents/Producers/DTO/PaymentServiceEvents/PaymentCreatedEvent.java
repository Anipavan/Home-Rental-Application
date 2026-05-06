package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Published when a new rent invoice / payment record is created. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreatedEvent {
    private String eventType;       // "payment.created"
    private String paymentId;
    private String invoiceNumber;
    private String tenantId;
    private String flatId;
    private String ownerId;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Instant timestamp;
}
