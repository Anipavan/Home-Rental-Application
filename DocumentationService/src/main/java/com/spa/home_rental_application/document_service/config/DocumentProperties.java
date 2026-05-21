package com.spa.home_rental_application.document_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Externalised Document Service configuration. Bound from {@code app.documents.*}. */
@ConfigurationProperties(prefix = "app.documents")
@Getter
@Setter
public class DocumentProperties {

    /** Storage backend selector: LOCAL | S3. */
    private String storageBackend = "LOCAL";

    /** Local filesystem dir when {@code storageBackend=LOCAL}. */
    private String localDir = "uploads/documents";

    /** S3 bucket name when {@code storageBackend=S3}. */
    private String s3Bucket;

    /** AWS region for the S3 bucket (e.g. {@code ap-south-1} for Mumbai). */
    private String s3Region = "ap-south-1";

    /** Optional S3-compatible endpoint override (e.g. MinIO, LocalStack). */
    private String s3EndpointOverride;

    /** Optional prefix prepended to every key (e.g. {@code prod/}). */
    private String s3KeyPrefix = "";

    /** When true, AWS SDK uses path-style addressing (needed for MinIO / LocalStack). */
    private boolean s3PathStyleAccess = false;

    /** TTL for pre-signed download URLs (architecture says 15 minutes). */
    private long downloadUrlTtlSeconds = 900L;

    /** HMAC secret for signing pre-signed URLs. */
    private String downloadUrlSecret = "change-me-in-prod";

    /**
     * Public base URL the BROWSER will use to fetch document blobs.
     *
     * <p>Defaults to a RELATIVE path ({@code /rentals/v1}) so the
     * browser resolves the pre-signed URL against whatever origin is
     * currently serving the SPA — works on localhost, ngrok-tunneled
     * dev sessions, AND production without any host-bound config.
     *
     * <p>Override via environment in any non-local environment where
     * the document blobs are served from a different origin than the
     * SPA itself (e.g. {@code PUBLIC_BASE_URL=https://anirudhhomes.in/rentals/v1}).
     *
     * <p>Earlier code defaulted to {@code http://localhost:8080/rentals/v1}
     * (absolute). That broke avatar rendering whenever the SPA was
     * served on any host other than localhost, because the absolute
     * URL embedded in {@code User.profilePictureUrl} pinned the
     * browser to localhost:8080 — unreachable from ngrok / staging
     * hosts (Issue #1).
     */
    private String publicBaseUrl = "/rentals/v1";

    /** Allow-list of multipart content-types we'll accept. */
    private List<String> allowedContentTypes = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg");

    private Ocr ocr = new Ocr();
    private Sandbox sandbox = new Sandbox();

    @Getter @Setter
    public static class Ocr {
        /** STUB | TESSERACT | SANDBOX. TESSERACT requires native binaries; SANDBOX hits a paid API. */
        private String provider = "STUB";

        /**
         * When true, every successful upload auto-triggers OCR via an async
         * event listener. Set to false for batch / on-demand-only flows
         * (admins still hit POST /documents/{id}/extract manually).
         */
        private boolean autoExtractOnUpload = true;

        /**
         * Hard cap on file size we'll ship to a paid OCR API. Sandbox accepts
         * ~5MB but we cap at 4MB to leave headroom for their multipart envelope.
         * Larger files are stored normally but OCR is skipped (ocrStatus=FAILED
         * with reason FILE_TOO_LARGE).
         */
        private long maxOcrFileSizeBytes = 4L * 1024 * 1024;
    }

    /**
     * Sandbox.co.in (Quicko) OCR provider config. Uses the same credentials
     * as the KYC service — Sandbox issues one set of keys per account that
     * unlocks PAN verify, Aadhaar OCR, and PAN OCR. So in production both
     * KYC_SERVICE and DOC_SERVICE read SANDBOX_API_KEY/SECRET from the same
     * .env.prod file.
     *
     * <p>Pricing (Sandbox sandbox / production):
     * <ul>
     *   <li>Aadhaar OCR — first 50 free, ~₹2.5/call after</li>
     *   <li>PAN OCR     — first 50 free, ~₹2/call after</li>
     *   <li>PAN verify  — first 100 free, ~₹0.50/call after</li>
     * </ul>
     */
    @Getter @Setter
    public static class Sandbox {
        private String baseUrl = "https://api.sandbox.co.in";
        private String apiKey;
        private String apiSecret;
        private String apiVersion = "1.0";
        /** /authenticate path — yields a short-lived JWT. */
        private String authPath = "/authenticate";
        /** Aadhaar OCR endpoint. Configurable in case Sandbox renames the path. */
        private String aadhaarOcrPath = "/kyc/ocr/aadhaar";
        /** PAN OCR endpoint. */
        private String panOcrPath = "/kyc/ocr/pan";
        /** PAN verify path — used for the secondary fraud check after PAN OCR. */
        private String panVerifyPath = "/kyc/pan/verify";
        /** Refresh JWT this many seconds before its stated expiry. */
        private int tokenRefreshSafetySeconds = 60;
    }
}
