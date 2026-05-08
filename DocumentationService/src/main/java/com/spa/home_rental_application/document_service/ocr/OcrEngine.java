package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.Entities.Document;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Pluggable OCR / Document AI backend. The default implementation is a stub
 * — real OCR (Tesseract / Apache Tika / Document AI) plugs in by adding a
 * new {@link OcrEngine} bean with {@code @ConditionalOnProperty(provider=...)}.
 */
public interface OcrEngine {

    String name();

    /**
     * Extract structured fields from the document. Returns a result holding
     * the field map, fraud flag, and confidence. The caller persists this
     * onto the {@link Document} record.
     */
    OcrResult extract(Document document);

    record OcrResult(
            Map<String, String> fields,
            boolean fraudFlag,
            BigDecimal confidenceScore,
            String failureReason) {}
}
