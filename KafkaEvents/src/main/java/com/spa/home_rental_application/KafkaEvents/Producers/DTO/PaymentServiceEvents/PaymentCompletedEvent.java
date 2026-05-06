package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Published when a payment is successfully captured. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String eventType;       // "payment.completed"
    private String paymentId;
    private String tenantId;
    private String ownerId;
    private BigDecimal amount;
    private String paymentMethod;   // UPI, CARD, NET_BANKING, ...
    private String transactionId;   // gateway transaction reference
    private Instant paidDate;
    private Instant timestamp;
}
