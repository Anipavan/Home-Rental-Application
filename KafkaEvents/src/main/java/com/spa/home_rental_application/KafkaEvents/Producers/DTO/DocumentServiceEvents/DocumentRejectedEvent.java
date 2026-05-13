package com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Fired by document-service when an OWNER rejects a tenant-uploaded
 * document (Issue #9). Consumed by notification-service to fan a
 * {@code DOCUMENT_REJECTED} notification out to the tenant with the
 * owner's free-text rejection reason embedded in the body so the
 * tenant knows exactly what to fix before re-uploading.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRejectedEvent {
    private String eventType;           // "document.rejected"
    private String documentId;
    /** Tenant who uploaded the document — recipient of the notification. */
    private String userId;
    private String documentType;        // AADHAAR | PAN | AGREEMENT | PHOTO | OTHER
    /** authUserId of the owner who rejected. */
    private String decidedBy;
    /**
     * Free-text reason the owner provided ("photo too blurry", "address
     * doesn't match the rental address", etc.). Surfaced verbatim in
     * the tenant's notification body so they know what to fix.
     */
    private String rejectionReason;
    private LocalDateTime decidedAt;
    private LocalDateTime timestamp;
}
