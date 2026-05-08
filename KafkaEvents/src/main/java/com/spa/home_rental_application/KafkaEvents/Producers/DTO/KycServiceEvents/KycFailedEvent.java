package com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Published when KYC verification fails (e.g. Digio rejection, face mismatch,
 * fraud flag). Notification Service consumes this to alert the user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycFailedEvent {
    private String eventType;
    private String userId;
    private String kycProvider;
    private String failureReason;
    private String failureCode;       // e.g. AADHAAR_MISMATCH, FACE_LOW_SCORE
    private LocalDateTime failedAt;
    private LocalDateTime timestamp;
}
