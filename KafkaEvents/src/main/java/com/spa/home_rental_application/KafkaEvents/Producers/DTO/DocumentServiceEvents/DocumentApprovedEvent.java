package com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Fired by document-service when an OWNER approves a tenant-uploaded
 * document (Issue #9). Consumed by notification-service to fan a
 * {@code DOCUMENT_APPROVED} notification out to the tenant across
 * every channel (email + SMS + WhatsApp + bell).
 *
 * <p>Distinct from {@code document.verified} (admin / KYC-provider
 * verification) — that's a different concept and lives on a
 * different field on Document.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentApprovedEvent {
    private String eventType;           // "document.approved"
    private String documentId;
    /** Tenant who uploaded the document — recipient of the notification. */
    private String userId;
    private String documentType;        // AADHAAR | PAN | AGREEMENT | PHOTO | OTHER
    /** authUserId of the owner who approved. */
    private String decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime timestamp;
}
