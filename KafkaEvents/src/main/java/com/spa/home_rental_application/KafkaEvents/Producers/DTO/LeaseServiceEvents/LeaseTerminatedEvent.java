package com.spa.home_rental_application.KafkaEvents.Producers.DTO.LeaseServiceEvents;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Published when a lease is terminated early or by reaching its end date. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseTerminatedEvent {
    private String eventType;
    private String leaseId;
    private String tenantId;
    private String flatId;
    private String ownerId;
    private String terminationReason;     // EARLY_TERMINATION | EXPIRY | DEFAULT | MUTUAL
    private LocalDate terminatedOn;
    private LocalDateTime timestamp;
}
