package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.Entities.Document;
import com.spa.home_rental_application.document_service.storage.DocumentStorage;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tesseract-backed OCR engine.
 * <p>
 * <b>Runtime requirements</b>:
 * <ul>
 *   <li>The native {@code libtesseract} library on the OS path (Alpine:
 *       {@code apk add tesseract-ocr})</li>
 *   <li>{@code tessdata} language files. Bundled English + Hindi cover
 *       Aadhaar / PAN cards. Path is set via {@code TESSDATA_PREFIX} env
 *       (defaults to {@code /usr/share/tessdata}, which Alpine populates).</li>
 * </ul>
 * Activated when {@code app.documents.ocr.provider=TESSERACT}.
 * <p>
 * <b>Field extraction</b>: after raw OCR, we run a small set of regex
 * heuristics to pull structured fields off the recognised text. This is
 * intentionally simple — for higher accuracy plug in a Document AI service
 * (Google DocAI, Tesseract trained models, or Claude vision via the AI Gateway).
 */
@Component
@ConditionalOnProperty(prefix = "app.documents.ocr", name = "provider", havingValue = "TESSERACT")
@Slf4j
public class TesseractOcrEngine implements OcrEngine {

    /** Allow overriding tessdata location at deploy time. */
    private static final String TESSDATA_PATH =
            System.getenv().getOrDefault("TESSDATA_PREFIX", "/usr/share/tessdata");

    private static final Pattern PAN_PATTERN     = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b[2-9][0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}\\b");
    private static final Pattern DOB_PATTERN     = Pattern.compile("\\b([0-3]?[0-9])[\\-/]([0-1]?[0-9])[\\-/]((19|20)\\d{2})\\b");
    private static final Pattern NAME_LABEL      = Pattern.compile("(?im)^\\s*name\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .'\\-]{1,60})");

    private final DocumentStorage storage;

    public TesseractOcrEngine(DocumentStorage storage) {
        this.storage = storage;
    }

    @Override
    public String name() {
        return "TESSERACT";
    }

    @Override
    public OcrResult extract(Document document) {
        log.info("[TESSERACT] documentId={} type={} backend={}",
                document.getId(), document.getDocumentType(), storage.backendName());

        // Tess4j needs a real File. Stream the storage object to a temp file
        // and clean up afterwards.
        Path tmp = null;
        try {
            tmp = Files.createTempFile("doc-" + document.getId() + "-", suffixFor(document.getContentType()));
            try (InputStream in = storage.open(document.getStorageUrl())) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(TESSDATA_PATH);
            // English first; add Hindi when scanning Aadhaar back-side.
            tesseract.setLanguage("eng");

            String text = tesseract.doOCR(tmp.toFile());
            log.debug("[TESSERACT] OCR'd {} chars from documentId={}",
                    text.length(), document.getId());

            Map<String, String> fields = extractFields(document.getDocumentType(), text);
            // Confidence: tess4j doesn't expose an API-level confidence in
            // simple doOCR; we approximate from text length / extracted-field
            // count. Replace with the level-by-level confidence API for
            // production-grade scoring.
            BigDecimal confidence = new BigDecimal(
                    fields.size() >= 3 ? "0.85" :
                    fields.size() == 2 ? "0.70" :
                    fields.size() == 1 ? "0.50" : "0.30");
            return new OcrResult(fields, false, confidence, null);
        } catch (TesseractException ex) {
            log.error("[TESSERACT] OCR failed for documentId={}", document.getId(), ex);
            return new OcrResult(Map.of(), false, BigDecimal.ZERO, "OCR_FAILED: " + ex.getMessage());
        } catch (IOException ex) {
            log.error("[TESSERACT] I/O failed pulling object for documentId={}", document.getId(), ex);
            return new OcrResult(Map.of(), false, BigDecimal.ZERO, "STORAGE_FAILED: " + ex.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException cleanupErr) {
                    log.warn("Failed to delete temp OCR file {}: {}", tmp, cleanupErr.getMessage());
                }
            }
        }
    }

    /**
     * Regex-driven field extraction, dispatched by document type. Designed to
     * extract enough to feed the User Service auto-fill consumer + KYC Service;
     * unrecognised fields are silently dropped.
     */
    Map<String, String> extractFields(String documentType, String text) {
        if (text == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();

        if ("PAN".equalsIgnoreCase(documentType)) {
            Matcher pan = PAN_PATTERN.matcher(text);
            if (pan.find()) out.put("panNumber", pan.group());
            findName(text).ifPresent(name -> out.put("name", name));
            findDob(text).ifPresent(dob -> out.put("dob", dob));
        } else if ("AADHAAR".equalsIgnoreCase(documentType)) {
            Matcher aad = AADHAAR_PATTERN.matcher(text);
            if (aad.find()) {
                String compact = aad.group().replaceAll("\\s+", "");
                out.put("idNumberLast4", compact.substring(compact.length() - 4));
            }
            findName(text).ifPresent(name -> out.put("name", name));
            findDob(text).ifPresent(dob -> out.put("dob", dob));
            // Address is everything after the line containing "Address" — naive
            // but works for most Aadhaar layouts.
            int idx = lowerIndexOf(text, "address");
            if (idx >= 0) {
                String trail = text.substring(idx).replaceAll("(?im)^\\s*address\\s*:?", "")
                        .replaceAll("[\\r\\n]+", " ")
                        .replaceAll("\\s{2,}", " ")
                        .trim();
                if (trail.length() > 5) {
                    out.put("address", trail.substring(0, Math.min(trail.length(), 200)));
                }
            }
        } else {
            // Other types: just expose a trimmed extract for review.
            String trimmed = text.length() > 1000 ? text.substring(0, 1000) : text;
            out.put("extractedText", trimmed.trim());
        }
        return new HashMap<>(out);
    }

    private static java.util.Optional<String> findName(String text) {
        Matcher m = NAME_LABEL.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty()) return java.util.Optional.of(name);
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String> findDob(String text) {
        Matcher m = DOB_PATTERN.matcher(text);
        if (m.find()) {
            String dd = pad2(m.group(1));
            String mm = pad2(m.group(2));
            String yy = m.group(3);
            return java.util.Optional.of(yy + "-" + mm + "-" + dd); // ISO output for downstream LocalDate parsing
        }
        return java.util.Optional.empty();
    }

    private static String pad2(String s) {
        return s.length() == 1 ? "0" + s : s;
    }

    private static int lowerIndexOf(String text, String needle) {
        return text.toLowerCase(java.util.Locale.ROOT).indexOf(needle);
    }

    private static String suffixFor(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
            case "image/png"                 -> ".png";
            case "image/jpeg", "image/jpg"   -> ".jpg";
            case "application/pdf"           -> ".pdf";
            default                          -> ".bin";
        };
    }
}
