package com.spa.home_rental_application.auth_service.Utils;

import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * JWT helper. Secret + TTLs come from {@link JwtProperties}; never hardcoded.
 * Provides generation, signature/expiry validation, and claim extraction.
 */
@Component
public class JWTUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JWTUtil(JwtProperties props) {
        this.props = props;
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        // Support either base64 or raw secret
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(props.getSecret());
        } catch (IllegalArgumentException ex) {
            keyBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 bytes) once decoded");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        long ttlMillis = props.getAccessTokenValiditySeconds() * 1000L;
        Date now = new Date();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(authentication.getName())
                .claim("authorities", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parse(token).getPayload().getSubject();
    }

    public List<String> extractAuthorities(String token) {
        Object claim = parse(token).getPayload().get("authorities");
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
