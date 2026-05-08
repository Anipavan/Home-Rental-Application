package com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Published when a user uploads a document. AI Engine consumes this to
 * trigger OCR + Document AI; KYC Service uses it to attach proof to the
 * user's KYC record.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadedEvent {
    private String eventType;
    private String documentId;
    private String userId;
    private String documentType;       // AADHAAR | PAN | AGREEMENT | PHOTO | OTHER
    private String contentType;        // image/png, application/pdf, ...
    private Long fileSizeBytes;
    private String storageUrl;         // opaque storage path (NOT a public URL)
    private LocalDateTime uploadedAt;
    private LocalDateTime timestamp;
}
