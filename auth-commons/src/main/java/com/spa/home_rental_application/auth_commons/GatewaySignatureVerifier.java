package com.spa.home_rental_application.auth_commons;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signing helper. The same algorithm is used on both ends:
 * the API Gateway computes a signature and includes it on every proxied
 * request; downstream services recompute it from the received headers
 * and reject the request if they don't match.
 * <p>
 * Canonical string-to-sign:  {@code timestamp + ":" + method + ":" + path}
 */
public class GatewaySignatureVerifier {

    private static final String ALGO = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] keyBytes;
    private final long allowedClockSkewSeconds;

    public GatewaySignatureVerifier(String secret, long allowedClockSkewSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.internal-auth.secret must be configured");
        }
        // Accept either base64 or raw text — be lenient.
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            decoded = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (decoded.length < 16) {
            throw new IllegalStateException("Internal-auth secret must be at least 128 bits (16 bytes) once decoded");
        }
        this.keyBytes = decoded;
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
    }

    /** Compute the hex-encoded signature for a given request. */
    public String sign(long timestamp, String method, String path) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(keyBytes, ALGO));
            String canonical = timestamp + ":" + method + ":" + path;
            return HEX.formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC", ex);
        }
    }

    /**
     * Constant-time signature verification with timestamp freshness check.
     */
    public Outcome verify(String tsHeader, String sigHeader, String method, String path) {
        if (tsHeader == null || tsHeader.isBlank())   return Outcome.MISSING;
        if (sigHeader == null || sigHeader.isBlank()) return Outcome.MISSING;

        long ts;
        try {
            ts = Long.parseLong(tsHeader);
        } catch (NumberFormatException ex) {
            return Outcome.MALFORMED;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > allowedClockSkewSeconds) {
            return Outcome.STALE;
        }

        String expected = sign(ts, method, path);
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = sigHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b) ? Outcome.OK : Outcome.MISMATCH;
    }

    public enum Outcome {
        OK,
        MISSING,
        MALFORMED,
        STALE,
        MISMATCH;

        public boolean isValid() { return this == OK; }
    }
}
