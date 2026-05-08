package com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Published when a user's KYC (Aadhaar / Digio) verification succeeds.
 * Consumed by User Service (to flip kyc_status to VERIFIED) and
 * Notification Service (to message the user / owner).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerifiedEvent {
    private String eventType;
    private String userId;
    private String kycProvider;       // DIGIO | SIGNZY | MANUAL
    private String aadhaarHash;       // SHA-256, never plain text
    private String panNumber;         // optional, masked when logged
    private Boolean verified;
    private Double faceMatchScore;
    private String kycReferenceId;
    private LocalDateTime verifiedAt;
    private LocalDateTime timestamp;
}
