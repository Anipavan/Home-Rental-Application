package com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Published when a property is successfully registered with the state RERA
 * portal. Notification Service consumes this to alert the owner.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReraRegisteredEvent {
    private String eventType;
    private String propertyId;
    private String ownerId;
    private String state;                  // KARNATAKA | MAHARASHTRA | ...
    private String reraRegistrationNumber;
    private String reraPortalId;
    private String registrationStatus;     // REGISTERED | EXPIRED
    private LocalDateTime registeredAt;
    private LocalDate expiryDate;
    private LocalDateTime timestamp;
}
