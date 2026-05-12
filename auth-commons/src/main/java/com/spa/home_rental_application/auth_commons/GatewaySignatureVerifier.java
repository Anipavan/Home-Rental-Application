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

    /**
     * Audit M36: support multi-key verification for zero-downtime
     * HMAC rotation. The {@code keys} array carries the
     * "primary,then any fallbacks". The first key signs outbound
     * traffic; verify accepts a match against ANY key in the list.
     *
     * <p>Operational flow for a rotation:
     *   1. Day 0 — config has one key {@code [KEY_A]}. Signing + verify both use A.
     *   2. Day 1 — push new config with {@code [KEY_B, KEY_A]} to verifying
     *      services first (gateway + downstream). They sign new traffic with
     *      B but still accept signatures made with A.
     *   3. Day 1 (slightly later) — push {@code [KEY_B, KEY_A]} to signing
     *      callers too. Everyone signs with B now; in-flight requests still
     *      verify under A.
     *   4. Day 7 (after the longest cache + token TTL) — drop A from the
     *      list, leaving {@code [KEY_B]}. Rotation complete.
     *
     * <p>The constructor accepts either a single secret (back-compat) or a
     * comma-separated list. The first key is always the signer.
     */
    private final byte[][] keys;
    private final long allowedClockSkewSeconds;

    public GatewaySignatureVerifier(String secret, long allowedClockSkewSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.internal-auth.secret must be configured");
        }
        String[] parts = secret.split(",");
        byte[][] decoded = new byte[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isBlank()) {
                throw new IllegalStateException(
                        "app.internal-auth.secret contains an empty entry (index " + i + ")");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(p);
            } catch (IllegalArgumentException ex) {
                bytes = p.getBytes(StandardCharsets.UTF_8);
            }
            if (bytes.length < 16) {
                throw new IllegalStateException(
                        "Internal-auth secret #" + i + " must be at least 128 bits (16 bytes) once decoded");
            }
            decoded[i] = bytes;
        }
        this.keys = decoded;
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
    }

    /** Compute the hex-encoded signature with the PRIMARY key (index 0). */
    public String sign(long timestamp, String method, String path) {
        return signWith(keys[0], timestamp, method, path);
    }

    private static String signWith(byte[] keyBytes, long timestamp, String method, String path) {
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
     * Constant-time signature verification with timestamp freshness
     * check. Tries each configured key in order and returns OK on
     * the first match (audit M36).
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

        byte[] presented = sigHeader.getBytes(StandardCharsets.UTF_8);
        for (byte[] key : keys) {
            byte[] expected = signWith(key, ts, method, path).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, presented)) return Outcome.OK;
        }
        return Outcome.MISMATCH;
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
