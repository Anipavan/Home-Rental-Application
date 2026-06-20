package com.spa.home_rental_application.payment_service.payment_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parses + verifies the short-lived REG_PAY token minted by auth-service
 * for the paid maintainer-signup flow. Same signing key + issuer as
 * regular access tokens (configured via {@code app.jwt.secret} on both
 * services), but we ONLY accept tokens whose {@code purpose} claim is
 * {@code REG_PAY} — this prevents a stolen REG_PAY token from being
 * mis-used elsewhere AND prevents a regular access token from being
 * presented here to bypass the strict paymentId match.
 *
 * <p>Lives in payment-service rather than a shared module because no
 * other service needs to verify REG_PAY tokens — only the two
 * /payments/registration/* endpoints care.
 */
@Component
@Slf4j
public class RegistrationPaymentTokenVerifier {

    private final SecretKey key;

    public RegistrationPaymentTokenVerifier(
            @Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret must be configured for payment-service "
                            + "to verify REG_PAY tokens minted by auth-service");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Verify the token's signature + expiry + that
     * {@code claims.purpose='REG_PAY'} AND
     * {@code claims.paymentId=expectedPaymentId}. Returns the {@code uid}
     * claim (the payer's auth user id) on success; throws on any failure.
     *
     * <p>Throws {@link InvalidRegistrationTokenException} instead of
     * raw JWT exceptions so callers can map to a clean 401 / 403 without
     * leaking jjwt internals to the wire.
     */
    public String verifyForPayment(String bearerHeader, String expectedPaymentId) {
        String token = stripBearer(bearerHeader);
        if (token == null) {
            throw new InvalidRegistrationTokenException(
                    "Missing or malformed Authorization header for registration payment.");
        }
        Jws<Claims> jws;
        try {
            jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        } catch (JwtException ex) {
            throw new InvalidRegistrationTokenException(
                    "Registration payment token is invalid or expired.", ex);
        }
        Claims c = jws.getPayload();
        String purpose = c.get("purpose", String.class);
        if (!"REG_PAY".equals(purpose)) {
            log.warn("Rejected registration payment call: token purpose={} (expected REG_PAY)", purpose);
            throw new InvalidRegistrationTokenException(
                    "This token is not allowed for the registration payment endpoint.");
        }
        String paymentId = c.get("paymentId", String.class);
        if (paymentId == null || !paymentId.equals(expectedPaymentId)) {
            log.warn("Rejected registration payment call: paymentId mismatch token={} body={}",
                    paymentId, expectedPaymentId);
            throw new InvalidRegistrationTokenException(
                    "This token is not allowed for the supplied paymentId.");
        }
        String uid = c.get("uid", String.class);
        if (uid == null || uid.isBlank()) {
            throw new InvalidRegistrationTokenException(
                    "Registration payment token missing uid claim.");
        }
        return uid;
    }

    private static String stripBearer(String header) {
        if (header == null || header.isBlank()) return null;
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header.trim();
    }

    /** Thrown on any REG_PAY token failure; the controller maps to 401. */
    public static class InvalidRegistrationTokenException extends RuntimeException {
        public InvalidRegistrationTokenException(String msg) { super(msg); }
        public InvalidRegistrationTokenException(String msg, Throwable cause) { super(msg, cause); }
    }
}
