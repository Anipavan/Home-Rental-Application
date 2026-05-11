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
     * <p>Critical: this MUST be the gateway's externally-reachable URL,
     * NOT the document-service's internal hostname. Spring Cloud
     * Gateway routes via {@code lb://HRA-document-service}, which
     * means {@code request.getServerName()} on this side resolves to
     * the internal Eureka-registered host (e.g. {@code localhost:8091}).
     * If we built the pre-signed URL from that, the browser would try
     * to fetch a URL the gateway can't proxy, and the avatar / document
     * download would 404 / be blocked by the internal-auth filter.
     *
     * <p>Defaults to the local gateway. Override via environment in
     * any non-local environment (e.g. {@code PUBLIC_BASE_URL=https://api.hearth.app/rentals/v1}).
     */
    private String publicBaseUrl = "http://localhost:8080/rentals/v1";

    /** Allow-list of multipart content-types we'll accept. */
    private List<String> allowedContentTypes = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg");

    private Ocr ocr = new Ocr();

    @Getter @Setter
    public static class Ocr {
        /** STUB | TESSERACT (TESSERACT requires native binaries). */
        private String provider = "STUB";
    }
}
