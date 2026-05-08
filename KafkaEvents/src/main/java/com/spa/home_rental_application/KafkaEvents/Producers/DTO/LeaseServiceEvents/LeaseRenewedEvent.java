package com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Published when a lease is renewed (new end-date + possibly new rent). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseRenewedEvent {
    private String eventType;
    private String leaseId;
    private String tenantId;
    private String flatId;
    private String ownerId;
    private LocalDate previousEndDate;
    private LocalDate newEndDate;
    private BigDecimal previousRent;
    private BigDecimal newRent;
    private LocalDateTime renewedAt;
    private LocalDateTime timestamp;
}
