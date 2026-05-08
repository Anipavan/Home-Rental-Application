package com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Published by the daily cron when a lease is within {@code daysUntilExpiry}
 * of its end date. AI Engine consumes this to trigger a renewal-strategy
 * prediction; Notification Service alerts the tenant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseExpiringEvent {
    private String eventType;
    private String leaseId;
    private String tenantId;
    private String flatId;
    private String ownerId;
    private LocalDate endDate;
    private Integer daysUntilExpiry;
    private BigDecimal rentAmount;
    private LocalDateTime timestamp;
}
