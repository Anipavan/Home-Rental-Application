package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.Entities.Document;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
import com.spa.home_rental_application.document_service.storage.DocumentStorage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production OCR engine backed by Sandbox.co.in (Quicko).
 *
 * <p>Two-step verification flow:
 * <ol>
 *   <li><b>OCR</b> — Upload the stored document image to Sandbox's
 *       {@code /kyc/ocr/aadhaar} or {@code /kyc/ocr/pan} endpoint. They
 *       return structured fields (name, dob, document number, address)
 *       extracted from the image via their OCR + ML pipeline.</li>
 *   <li><b>Fraud cross-check</b> (PAN only) — Call {@code /kyc/pan/verify}
 *       with the OCR'd PAN + name. If NSDL says "INVALID" or the name
 *       match score is below threshold, we flag the document as
 *       {@code fraudFlag=true}. The owner sees this clearly in the
 *       review screen.</li>
 * </ol>
 *
 * <p>For Aadhaar we only OCR — UIDAI doesn't expose a "is this Aadhaar
 * number valid?" lookup to non-AUA entities, so we extract last-4 +
 * name + DOB for the owner's manual review without claiming legal
 * authenticity. (Full Aadhaar verification requires the user-driven
 * offline-eKYC ZIP upload flow, which is out of scope for this MVP.)
 *
 * <p>Activates only when {@code app.documents.ocr.provider=SANDBOX}.
 */
@Component
@ConditionalOnProperty(prefix = "app.documents.ocr", name = "provider", havingValue = "SANDBOX")
@Slf4j
public class SandboxOcrEngine implements OcrEngine {

    /** Name-match score below which we set fraudFlag=true on PAN docs. */
    private static final double NAME_MATCH_MIN_SCORE = 60.0;

    private final DocumentProperties props;
    private final RestTemplate http;
    private final SandboxOcrAuthClient authClient;
    private final DocumentStorage storage;

    public SandboxOcrEngine(DocumentProperties props,
                            RestTemplate sandboxOcrRestTemplate,
                            SandboxOcrAuthClient authClient,
                            DocumentStorage storage) {
        this.props = props;
        this.http = sandboxOcrRestTemplate;
        this.authClient = authClient;
        this.storage = storage;
    }

    @Override
    public String name() {
        return "SANDBOX";
    }

    /**
     * Wraps the call in {@code @CircuitBreaker} + {@code @Retryable} so
     * transient Sandbox outages produce a clean fraudFlag=false +
     * failureReason rather than a 5xx to the document upload caller.
     */
    @Override
    @CircuitBreaker(name = "sandbox-ocr-client", fallbackMethod = "extractFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 2, backoff = @Backoff(delay = 800, multiplier = 2))
    public OcrResult extract(Document document) {
        log.info("[SANDBOX-OCR] documentId={} type={}", document.getId(), document.getDocumentType());

        // Size gate before we burn a paid call on a huge file. The image
        // is too large for Sandbox anyway; better to fail clearly here.
        if (document.getFileSizeBytes() != null
                && document.getFileSizeBytes() > props.getOcr().getMaxOcrFileSizeBytes()) {
            log.warn("[SANDBOX-OCR] file too large for OCR documentId={} size={} cap={}",
                    document.getId(), document.getFileSizeBytes(),
                    props.getOcr().getMaxOcrFileSizeBytes());
            return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                    "File exceeds OCR size cap of "
                            + (props.getOcr().getMaxOcrFileSizeBytes() / 1024 / 1024) + "MB");
        }

        byte[] imageBytes;
        try (InputStream in = storage.open(document.getStorageUrl())) {
            imageBytes = in.readAllBytes();
        } catch (IOException ex) {
            log.error("[SANDBOX-OCR] couldn't load image for documentId={}", document.getId(), ex);
            return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                    "Could not load stored image: " + ex.getMessage());
        }

        try {
            return doExtract(document, imageBytes);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("[SANDBOX-OCR] 401 — invalidating cached token + retrying once");
            authClient.invalidate();
            return doExtract(document, imageBytes);
        } catch (HttpClientErrorException e) {
            log.warn("[SANDBOX-OCR] HTTP {} from Sandbox for documentId={}: {}",
                    e.getStatusCode(), document.getId(), e.getStatusText());
            return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                    "Sandbox OCR returned " + e.getStatusCode());
        }
    }

    private OcrResult doExtract(Document document, byte[] imageBytes) {
        String type = document.getDocumentType() == null ? "OTHER" : document.getDocumentType().toUpperCase();
        return switch (type) {
            case "AADHAAR" -> ocrAadhaar(document, imageBytes);
            case "PAN" -> ocrPanAndVerify(document, imageBytes);
            default -> {
                // Other doc types — Sandbox doesn't have specialised OCR;
                // we just record a stub result so the document still
                // reaches the owner's review queue.
                log.info("[SANDBOX-OCR] no Sandbox OCR for type={}, returning empty result", type);
                yield new OcrResult(Map.of(), false, BigDecimal.ZERO,
                        "No automated OCR for document type " + type);
            }
        };
    }

    /**
     * Calls Sandbox's Aadhaar OCR endpoint. Returns the extracted fields
     * for the owner's review — never claims the Aadhaar is "verified"
     * since OCR alone can't prove authenticity (a photoshopped card
     * would still OCR successfully).
     */
    private OcrResult ocrAadhaar(Document document, byte[] imageBytes) {
        Map<String, Object> resp = multipartCall(
                props.getSandbox().getAadhaarOcrPath(),
                document, imageBytes);
        Map<String, Object> data = unwrapData(resp);
        if (data == null) {
            return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                    "Sandbox Aadhaar OCR returned no data");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        putIfPresent(fields, "name", data.get("name"));
        putIfPresent(fields, "dob", data.get("date_of_birth"));
        putIfPresent(fields, "gender", data.get("gender"));
        putIfPresent(fields, "address", data.get("address"));

        // Aadhaar number — store ONLY the last 4 digits per DPDP minimisation.
        String aadhaar = strOf(data.get("aadhaar_number"));
        if (aadhaar != null && aadhaar.length() >= 4) {
            String compact = aadhaar.replaceAll("\\s+", "");
            fields.put("idNumberLast4", compact.substring(Math.max(0, compact.length() - 4)));
        }

        BigDecimal confidence = numOf(data.get("confidence"), 0.80);
        log.info("[SANDBOX-OCR] AADHAAR documentId={} name={} dob={} last4={} confidence={}",
                document.getId(), fields.get("name"), fields.get("dob"),
                fields.get("idNumberLast4"), confidence);
        return new OcrResult(fields, false, confidence, null);
    }

    /**
     * Calls Sandbox's PAN OCR endpoint, then cross-checks the extracted
     * PAN + name against NSDL via {@code /kyc/pan/verify}. Flags fraud
     * when:
     *  - NSDL returns INVALID for the OCR'd PAN, OR
     *  - The name on the card doesn't match the NSDL-registered name.
     */
    private OcrResult ocrPanAndVerify(Document document, byte[] imageBytes) {
        Map<String, Object> ocrResp = multipartCall(
                props.getSandbox().getPanOcrPath(),
                document, imageBytes);
        Map<String, Object> data = unwrapData(ocrResp);
        if (data == null) {
            return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                    "Sandbox PAN OCR returned no data");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        String panNumber = strOf(data.get("pan_number"));
        String name = strOf(data.get("name"));
        putIfPresent(fields, "panNumber", panNumber);
        putIfPresent(fields, "name", name);
        putIfPresent(fields, "dob", data.get("date_of_birth"));
        putIfPresent(fields, "fatherName", data.get("father_name"));

        BigDecimal ocrConfidence = numOf(data.get("confidence"), 0.80);

        // Cross-check via NSDL PAN verify. This is what turns OCR into
        // actual fraud detection — if the PAN doesn't exist or the name
        // mismatches, the document is suspicious.
        boolean fraudFlag = false;
        String failureReason = null;
        if (panNumber != null && panNumber.matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
            try {
                PanVerifyResult verify = verifyPanWithNsdl(panNumber, name);
                if (!verify.valid()) {
                    fraudFlag = true;
                    failureReason = "PAN not found on NSDL — possible forgery";
                } else if (name != null && verify.score() < NAME_MATCH_MIN_SCORE) {
                    fraudFlag = true;
                    failureReason = "Name on card doesn't match NSDL registration (score "
                            + Math.round(verify.score()) + "%)";
                } else if (verify.registeredName() != null) {
                    // Replace the OCR'd name with the NSDL-registered one —
                    // canonical source of truth, and the casing is consistent.
                    fields.put("nameOnNsdl", verify.registeredName());
                }
            } catch (Exception ex) {
                // Verify failed but OCR succeeded — don't fail the whole
                // upload; surface a soft warning the owner can act on.
                log.warn("[SANDBOX-OCR] PAN verify cross-check failed for documentId={}: {}",
                        document.getId(), ex.getMessage());
                failureReason = "Cross-check via NSDL unavailable — owner must verify manually";
            }
        }

        log.info("[SANDBOX-OCR] PAN documentId={} pan=****{} fraud={} reason={}",
                document.getId(),
                panNumber == null ? "??" : panNumber.substring(Math.max(0, panNumber.length() - 2)),
                fraudFlag, failureReason);
        return new OcrResult(fields, fraudFlag, ocrConfidence, failureReason);
    }

    /**
     * Calls /kyc/pan/verify with the OCR'd PAN + name. Returns whether
     * the PAN exists on NSDL, the registered holder name, and the
     * fuzzy name-match score.
     */
    private PanVerifyResult verifyPanWithNsdl(String pan, String nameAsPerCard) {
        HttpHeaders headers = sandboxHeaders(MediaType.APPLICATION_JSON);
        String body = new JSONObject()
                .put("@entity", "in.co.sandbox.kyc.pan_verification.request")
                .put("pan", pan)
                .put("name_as_per_pan", nameAsPerCard == null ? "" : nameAsPerCard)
                .put("consent", "Y")
                .put("reason", "Document OCR fraud cross-check")
                .toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getSandbox().getBaseUrl() + props.getSandbox().getPanVerifyPath(),
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> data = unwrapData(resp);
        if (data == null) return new PanVerifyResult(false, null, 0.0);
        String status = strOf(data.get("status"));
        if (!"VALID".equalsIgnoreCase(status)) return new PanVerifyResult(false, null, 0.0);
        return new PanVerifyResult(
                true,
                strOf(data.get("full_name")),
                numDouble(data.get("name_match_score")));
    }

    /** Shared multipart POST helper for the two OCR endpoints. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> multipartCall(String path, Document doc, byte[] imageBytes) {
        HttpHeaders headers = sandboxHeaders(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Wrap bytes in a Resource subclass that exposes a filename — Spring's
        // form converter needs this or the multipart "file" part shows up
        // without Content-Disposition filename, which Sandbox rejects.
        body.add("file", new NamedByteResource(imageBytes, safeFilename(doc)));

        return http.postForObject(
                props.getSandbox().getBaseUrl() + path,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    /** Sandbox headers — Authorization (JWT) + x-api-key + x-api-version. */
    private HttpHeaders sandboxHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", authClient.getAccessToken());
        headers.set("x-api-key", props.getSandbox().getApiKey());
        headers.set("x-api-version", props.getSandbox().getApiVersion());
        return headers;
    }

    @SuppressWarnings("unused")
    private OcrResult extractFallback(Document document, Throwable ex) {
        log.error("[SANDBOX-OCR] circuit open / failed for documentId={}", document.getId(), ex);
        return new OcrResult(Map.of(), false, BigDecimal.ZERO,
                "OCR service temporarily unavailable — owner will verify manually");
    }

    /* ---------- helpers ---------- */

    /** Unwrap Sandbox's {"code":200, "data": {...}} envelope. Returns null on non-200. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> resp) {
        if (resp == null) return null;
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() != 200) return null;
        Object data = resp.get("data");
        return data instanceof Map<?, ?> ? (Map<String, Object>) data : null;
    }

    private static void putIfPresent(Map<String, String> map, String key, Object val) {
        if (val == null) return;
        String s = String.valueOf(val).trim();
        if (!s.isEmpty()) map.put(key, s);
    }

    private static String strOf(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal numOf(Object o, double dflt) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o == null) return BigDecimal.valueOf(dflt);
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(dflt);
        }
    }

    private static double numDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String safeFilename(Document doc) {
        String orig = doc.getOriginalFilename();
        if (orig != null && !orig.isBlank()) return orig;
        // Synthesize one from contentType so Sandbox has a usable extension.
        String ct = doc.getContentType();
        String ext = ct == null ? "bin"
                : switch (ct.toLowerCase()) {
                    case "image/png" -> "png";
                    case "image/jpeg", "image/jpg" -> "jpg";
                    case "application/pdf" -> "pdf";
                    default -> "bin";
                };
        return "doc-" + doc.getId() + "." + ext;
    }

    /** Internal result of the PAN-verify cross-check. */
    private record PanVerifyResult(boolean valid, String registeredName, double score) {}

    /**
     * ByteArrayResource subclass that exposes a filename. Spring's
     * FormHttpMessageConverter uses getFilename() to populate the
     * multipart {@code Content-Disposition: filename="..."} header
     * that Sandbox's gateway requires.
     */
    private static final class NamedByteResource extends ByteArrayResource {
        private final String filename;
        NamedByteResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }
        @Override
        public String getFilename() {
            return filename;
        }
    }
}
