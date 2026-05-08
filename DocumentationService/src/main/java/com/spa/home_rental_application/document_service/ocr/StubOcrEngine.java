package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.Entities.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Deterministic stub used in dev / CI. Pretends to extract fields based
 * on the {@code documentType} so KYC and User Service consumers can be
 * exercised end-to-end without a real OCR binary.
 */
@Component
@ConditionalOnProperty(prefix = "app.documents.ocr", name = "provider",
        havingValue = "STUB", matchIfMissing = true)
@Slf4j
public class StubOcrEngine implements OcrEngine {

    @Override
    public String name() {
        return "STUB";
    }

    @Override
    public OcrResult extract(Document document) {
        log.info("[STUB-OCR] extracting documentId={} type={}",
                document.getId(), document.getDocumentType());

        Map<String, String> fields = switch (document.getDocumentType() == null
                ? "OTHER" : document.getDocumentType().toUpperCase()) {
            case "AADHAAR" -> Map.of(
                    "name", "Test User",
                    "dob", "1990-01-01",
                    "idNumberLast4", "0000",
                    "address", "Stub address, Bengaluru, KA");
            case "PAN" -> Map.of(
                    "name", "Test User",
                    "panNumber", "ABCDE1234F",
                    "dob", "1990-01-01");
            case "AGREEMENT" -> Map.of(
                    "documentTitle", "Rental Agreement",
                    "extractedText", "Stub extracted text — replace with Tika output");
            default -> Map.of("note", "Stub OCR — no fields extracted for type "
                    + document.getDocumentType());
        };

        return new OcrResult(fields, false, new BigDecimal("0.85"), null);
    }
}
