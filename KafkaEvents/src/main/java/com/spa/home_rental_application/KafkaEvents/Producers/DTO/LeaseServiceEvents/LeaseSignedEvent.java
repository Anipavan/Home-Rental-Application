package com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Published when a new lease is signed (status flips DRAFT → ACTIVE). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseSignedEvent {
    private String eventType;
    private String leaseId;
    private String leaseNumber;
    private String tenantId;
    private String flatId;
    private String ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private BigDecimal securityDeposit;
    private LocalDateTime timestamp;
}
