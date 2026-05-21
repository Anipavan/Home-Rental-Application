package com.spa.home_rental_application.document_service.ocr;

/**
 * In-process Spring event fired by {@code DocumentServiceImpl.upload}
 * after a document row is persisted. Listened to by
 * {@link DocumentAutoExtractListener} which kicks off OCR asynchronously
 * so the upload response returns to the client in &lt;100ms regardless
 * of how long Sandbox takes to OCR the image.
 *
 * <p>Distinct from the Kafka {@code document.uploaded} event — that one
 * is for cross-service consumers (User Service auto-fill, KYC Service).
 * This one stays inside DocumentationService and exists purely to
 * decouple the synchronous upload path from the background OCR work.
 */
public record DocumentUploadedInternalEvent(
        String documentId,
        String userId,
        String documentType
) {
}
