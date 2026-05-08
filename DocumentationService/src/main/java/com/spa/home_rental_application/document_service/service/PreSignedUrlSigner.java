package com.spa.home_rental_application.document_service.service;

import com.spa.home_rental_application.document_service.Exceptionclass.InvalidPreSignedUrlException;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * HMAC-SHA256 signer / verifier for pre-signed download URLs.
 * <p>
 * URL form (query params): {@code ?expires={epochSec}&signature={base64}}.
 * The signed payload is {@code documentId || ":" || expires}.
 * <p>
 * In production we'd hand off to an S3 pre-signer or AWS KMS-backed key;
 * the HMAC scheme keeps us self-contained for the LOCAL backend.
 */
@Component
@Slf4j
public class PreSignedUrlSigner {

    private static final String ALGO = "HmacSHA256";

    private final DocumentProperties props;

    public PreSignedUrlSigner(DocumentProperties props) {
        this.props = props;
    }

    public Signed sign(String documentId) {
        long expires = Instant.now().getEpochSecond() + props.getDownloadUrlTtlSeconds();
        String signature = compute(documentId, expires);
        return new Signed(expires, signature,
                LocalDateTime.ofInstant(Instant.ofEpochSecond(expires), ZoneId.systemDefault()));
    }

    /** Throws {@link InvalidPreSignedUrlException} if signature missing / wrong / expired. */
    public void verify(String documentId, long expires, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new InvalidPreSignedUrlException("Missing signature on download URL");
        }
        if (Instant.now().getEpochSecond() > expires) {
            throw new InvalidPreSignedUrlException("Pre-signed URL has expired");
        }
        String expected = compute(documentId, expires);
        if (!constantTimeEquals(expected, signature)) {
            throw new InvalidPreSignedUrlException("Invalid signature on download URL");
        }
    }

    private String compute(String documentId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(props.getDownloadUrlSecret().getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] raw = mac.doFinal((documentId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign download URL", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public record Signed(long expiresEpochSec, String signature, LocalDateTime expiresAt) {}
}
