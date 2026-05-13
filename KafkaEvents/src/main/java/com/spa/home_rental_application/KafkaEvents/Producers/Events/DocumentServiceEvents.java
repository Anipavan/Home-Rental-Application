package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentApprovedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentRejectedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentUploadedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentVerifiedEvent;

/** Producer contract for Document Service domain events. */
public interface DocumentServiceEvents {
    void sendDocumentUploaded(DocumentUploadedEvent event);
    void sendDocumentVerified(DocumentVerifiedEvent event);
    void sendDocumentExtracted(DocumentExtractedEvent event);

    /** Issue #9 — owner approved a tenant-uploaded document. */
    void sendDocumentApproved(DocumentApprovedEvent event);

    /** Issue #9 — owner rejected a tenant-uploaded document. */
    void sendDocumentRejected(DocumentRejectedEvent event);
}
