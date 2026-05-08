package com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Published when a document has been verified (manually by an admin, by KYC
 * Service after Aadhaar match, or auto-verified by Document AI).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerifiedEvent {
    private String eventType;
    private String documentId;
    private String userId;
    private String documentType;
    private String verifiedBy;          // SYSTEM | ADMIN | KYC_PROVIDER
    private Boolean fraudFlag;
    private LocalDateTime verifiedAt;
    private LocalDateTime timestamp;
}
