package com.spa.home_rental_application.api_gateway.api_gateway.utils;

import com.spa.home_rental_application.api_gateway.api_gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Validates access tokens issued by Auth Service.
 *
 * <p>Returns a structured {@link Validation} so the gateway filter can
 * distinguish "valid", "expired" (→ client should call /rentals/v1/auth/refresh)
 * and "malformed/invalid" (→ client must re-login).
 */
@Component
public class JWTUtil {

    private final SecretKey key;
    private final String expectedIssuer;

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
    }

    public Validation validate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims c = jws.getPayload();
            if (expectedIssuer != null && !expectedIssuer.equals(c.getIssuer())) {
                return Validation.invalid("Issuer mismatch");
            }
            return Validation.ok(c);
        } catch (ExpiredJwtException ex) {
            return Validation.expired(ex.getMessage());
        } catch (JwtException ex) {
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
