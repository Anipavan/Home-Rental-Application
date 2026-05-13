package com.spa.home_rental_application.notification_service.notification_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentApprovedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentRejectedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentVerifiedEvent;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subscribes to {@code document-events} (published by Document Service).
 * <p>
 * We deliberately do NOT notify on {@code document.uploaded} — that's
 * usually triggered by the user themselves uploading from the UI, so a
 * confirmation message would be noise. We notify on verification and
 * extraction completion (admin- / system-driven outcomes).
 */
@Component
@Slf4j
public class DocumentEventListener {

    private final NotificationService notifications;

    public DocumentEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = "${app.kafka.document-topic:document-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-document-verified",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentVerifiedEvent"}
    )
    public void onVerified(DocumentVerifiedEvent e) {
        if (e == null || !"document.verified".equals(e.getEventType())) return;
        log.info("Received {} for documentId={} fraud={}",
                e.getEventType(), e.getDocumentId(), e.getFraudFlag());
        notifications.sendFromTemplate(e.getUserId(), NotificationType.EMAIL,
                NotificationCategory.DOCUMENT_VERIFIED,
                Map.of("documentType", safe(e.getDocumentType()),
                        "verifiedBy",  safe(e.getVerifiedBy()),
                        "fraudFlag",   safe(e.getFraudFlag()),
                        "verifiedAt",  safe(e.getVerifiedAt())));
    }

    @KafkaListener(
            topics = "${app.kafka.document-topic:document-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-document-extracted",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent"}
    )
    public void onExtracted(DocumentExtractedEvent e) {
        if (e == null || !"document.extracted".equals(e.getEventType())) return;
        log.info("Received {} for documentId={} confidence={}",
                e.getEventType(), e.getDocumentId(), e.getConfidenceScore());
        notifications.sendFromTemplate(e.getUserId(), NotificationType.EMAIL,
                NotificationCategory.DOCUMENT_EXTRACTED,
                Map.of("documentType",    safe(e.getDocumentType()),
                        "confidenceScore",safe(e.getConfidenceScore()),
                        "fraudFlag",      safe(e.getFraudFlag()),
                        "extractedAt",    safe(e.getExtractedAt())));
    }

    /**
     * Issue #9 — owner approved the tenant's document. Fan a tenant-
     * facing confirmation across every channel they're configured on
     * (email + SMS + WhatsApp + bell) so they know the document
     * landed without having to refresh the documents tab.
     */
    @KafkaListener(
            topics = "${app.kafka.document-topic:document-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-document-approved",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentApprovedEvent"}
    )
    public void onApproved(DocumentApprovedEvent e) {
        if (e == null || !"document.approved".equals(e.getEventType())) return;
        log.info("Received {} for documentId={} userId={}",
                e.getEventType(), e.getDocumentId(), e.getUserId());
        notifications.fanOut(e.getUserId(),
                NotificationCategory.DOCUMENT_APPROVED,
                Map.of("documentType", safe(e.getDocumentType()),
                        "decidedAt",  safe(e.getDecidedAt())));
    }

    /**
     * Issue #9 — owner rejected the tenant's document. Includes the
     * reason verbatim in the notification body so the tenant knows
     * exactly what to fix before re-uploading.
     */
    @KafkaListener(
            topics = "${app.kafka.document-topic:document-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-notification-service}-document-rejected",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentRejectedEvent"}
    )
    public void onRejected(DocumentRejectedEvent e) {
        if (e == null || !"document.rejected".equals(e.getEventType())) return;
        log.info("Received {} for documentId={} userId={} reason={}",
                e.getEventType(), e.getDocumentId(), e.getUserId(), e.getRejectionReason());
        notifications.fanOut(e.getUserId(),
                NotificationCategory.DOCUMENT_REJECTED,
                Map.of("documentType",    safe(e.getDocumentType()),
                        "rejectionReason",safe(e.getRejectionReason()),
                        "decidedAt",      safe(e.getDecidedAt())));
    }

    private static String safe(Object o) { return o == null ? "" : o.toString(); }
}
