package com.spa.home_rental_application.api_gateway.api_gateway.utils;

import com.spa.home_rental_application.api_gateway.api_gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Validates access tokens issued by Auth Service.
 *
 * <p>Returns a structured {@link Validation} so the gateway filter can
 * distinguish "valid", "expired" (→ client should call /rentals/v1/auth/refresh)
 * and "malformed/invalid" (→ client must re-login).
 */
@Component
@Slf4j
public class JWTUtil {

    private final SecretKey key;
    private final String expectedIssuer;
    /**
     * SHA-256 of the secret bytes, hex-encoded. Logged at startup so an
     * operator can compare the gateway's key against auth-service's
     * (auth-service emits the same hash). Logs ONLY the hash, never
     * the secret itself.
     */
    private final String keyFingerprint;

    public JWTUtil(JwtProperties props) {
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(props.getSecret());
        } catch (IllegalArgumentException ex) {
            bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expectedIssuer = props.getIssuer();
        this.keyFingerprint = fingerprint(bytes);
        log.info("JWTUtil initialised: expectedIssuer={} keyFingerprint=sha256:{} keyBytes={}",
                expectedIssuer, keyFingerprint, bytes.length);
    }

    private static String fingerprint(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(bytes);
            // First 8 bytes = 16 hex chars — enough to compare visually,
            // not enough to brute the key.
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    public Validation validate(String token) {
        try {
            // ROLLBACK: removed the explicit alg-header pinning (C6).
            // JJWT 0.12's verifyWith(SecretKey) already restricts to
            // HMAC-SHA* algorithms — the extra check was paranoid and
            // could reject tokens whose alg header capitalization or
            // formatting differed from "HS256" verbatim.
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims c = jws.getPayload();
            if (expectedIssuer != null && !expectedIssuer.equals(c.getIssuer())) {
                log.warn("JWT rejected: issuer mismatch (expected={} actual={})",
                        expectedIssuer, c.getIssuer());
                return Validation.invalid("Issuer mismatch");
            }
            return Validation.ok(c);
        } catch (ExpiredJwtException ex) {
            return Validation.expired(ex.getMessage());
        } catch (JwtException ex) {
            // Diagnostic log: the gateway's "Invalid access token" toast is
            // useless without server-side context. SignatureException usually
            // means key drift between auth-service (signer) and gateway
            // (verifier). Logging the exception class lets ops narrow down
            // the root cause in one glance.
            log.warn("JWT rejected: {} — {} (gateway keyFingerprint=sha256:{})",
                    ex.getClass().getSimpleName(), ex.getMessage(), keyFingerprint);
            return Validation.invalid(ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractAuthorities(Claims c) {
        Object claim = c.get("authorities");
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public sealed interface Validation
            permits Validation.Ok, Validation.Expired, Validation.Invalid {

        static Ok      ok(Claims c)        { return new Ok(c); }
        static Expired expired(String msg) { return new Expired(msg); }
        static Invalid invalid(String msg) { return new Invalid(msg); }

        record Ok(Claims claims) implements Validation {}
        record Expired(String message) implements Validation {}
        record Invalid(String message) implements Validation {}
    }
}
