package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.config.DocumentProperties;
import com.spa.home_rental_application.document_service.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link DocumentUploadedInternalEvent} (fired by
 * {@code DocumentServiceImpl.upload} after a row is persisted) and
 * kicks off OCR asynchronously.
 *
 * <p>Why a listener and not an inline call in {@code upload}?
 * <ul>
 *   <li><b>Latency</b> — Sandbox OCR can take 5-10s. We want the
 *       upload POST to return immediately so the tenant's UI doesn't
 *       hang. The frontend polls /documents/{id} for the OCR result.</li>
 *   <li><b>Transaction safety</b> — {@code upload} is {@code @Transactional}.
 *       Calling {@code documentService.extract()} synchronously from
 *       the same class would skip the proxy and bypass the transaction
 *       boundary of {@code extract}. The event-listener pattern lets
 *       Spring resolve the proxy normally.</li>
 *   <li><b>Failure isolation</b> — if Sandbox is down, the upload
 *       still succeeds. The document sits in {@code ocrStatus=FAILED}
 *       until an admin manually re-triggers /extract, or the next
 *       deploy retries.</li>
 * </ul>
 *
 * <p>The {@code @Async} annotation needs {@code @EnableAsync} on the
 * app config (see {@link AsyncOcrConfig}).
 */
@Component
@Slf4j
public class DocumentAutoExtractListener {

    private final DocumentService documentService;
    private final DocumentProperties props;

    public DocumentAutoExtractListener(DocumentService documentService,
                                       DocumentProperties props) {
        this.documentService = documentService;
        this.props = props;
    }

    @Async("ocrExecutor")
    @EventListener
    public void onUploaded(DocumentUploadedInternalEvent event) {
        if (!props.getOcr().isAutoExtractOnUpload()) {
            log.debug("auto-extract disabled — skipping OCR for documentId={}", event.documentId());
            return;
        }
        log.info("Auto-extract starting documentId={} type={}",
                event.documentId(), event.documentType());
        try {
            documentService.extract(event.documentId());
            log.info("Auto-extract completed documentId={}", event.documentId());
        } catch (Exception ex) {
            // Already logged inside extract() with the underlying cause;
            // here we only WARN so a Sandbox blip doesn't fill the log
            // with stack traces. extract() has flipped ocrStatus=FAILED
            // by the time we reach this catch.
            log.warn("Auto-extract failed for documentId={} (admin can retry): {}",
                    event.documentId(), ex.getMessage());
        }
    }
}
