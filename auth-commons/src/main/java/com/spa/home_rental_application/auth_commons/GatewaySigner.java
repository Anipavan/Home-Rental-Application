package com.spa.home_rental_application.auth_commons;

import java.time.Instant;

/**
 * Signs requests at the API Gateway. Returns a {@link Signature} pair the
 * caller writes into the {@code X-Internal-Auth-Sig} and
 * {@code X-Internal-Auth-Ts} headers.
 */
public class GatewaySigner {

    private final GatewaySignatureVerifier verifier;

    public GatewaySigner(String secret, long allowedClockSkewSeconds) {
        this.verifier = new GatewaySignatureVerifier(secret, allowedClockSkewSeconds);
    }

    public Signature sign(String method, String path) {
        long ts = Instant.now().getEpochSecond();
        String sig = verifier.sign(ts, method.toUpperCase(), path);
        return new Signature(ts, sig);
    }

    public record Signature(long timestamp, String signature) {}
}
