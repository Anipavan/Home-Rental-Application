package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String eventType;       // "payment.failed"
    private String paymentId;
    private String tenantId;
    private String reason;
    private String gatewayErrorCode;
    private Instant timestamp;
}
