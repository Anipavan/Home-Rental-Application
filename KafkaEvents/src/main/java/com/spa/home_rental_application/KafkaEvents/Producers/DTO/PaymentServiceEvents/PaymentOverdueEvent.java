package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOverdueEvent {
    private String eventType;       // "payment.overdue"
    private String paymentId;
    private String tenantId;
    private long daysOverdue;
    private BigDecimal amount;
    private BigDecimal lateFee;
    private Instant timestamp;
}
