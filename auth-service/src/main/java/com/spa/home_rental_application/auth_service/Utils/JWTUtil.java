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

    /**
     * Legacy overload — kept so any caller without the auth-tier user id
     * handy still compiles. Falls through to the uid-aware overload
     * with a {@code null} id, which means the downstream X-Auth-User-Id
     * header arrives blank. Prefer {@link #generateToken(Authentication, Object)}.
     */
    public String generateToken(Authentication authentication) {
        return generateToken(authentication, null);
    }

    /**
     * Full overload — embeds the auth-tier user id as a {@code uid}
     * claim. The API Gateway's JWTAuthenticationFilter reads this claim
     * and stamps it onto the {@code X-Auth-User-Id} request header that
     * downstream services key all per-user data on (wishlist favourites,
     * notification preferences, owner-scoped queries, etc.).
     *
     * <p>Without the uid claim, downstream controllers receive an empty
     * string as the auth user id — which then hits Oracle as
     * {@code "" → NULL} and explodes with ORA-01400 on any per-user
     * INSERT. That's the bug that surfaced on the wishlist's first
     * real toggle.
     *
     * @param uid the auth-service primary key for the authenticated
     *            user, or {@code null} when the caller doesn't have it
     *            handy (the claim is omitted in that case).
     */
    public String generateToken(Authentication authentication, Object uid) {
        long ttlMillis = props.getAccessTokenValiditySeconds() * 1000L;
        Date now = new Date();

        // Audit M5: the JWT's `sub` is the immutable user id (when
        // we have one) so a future username change doesn't orphan
        // outstanding tokens. The display username is moved to a
        // separate `username` claim. Gateway reads `uid` (still set
        // below) to populate X-Auth-User-Id so this change is
        // backwards-compatible at the consumer end.
        String stableSubject = uid != null ? uid.toString() : authentication.getName();
        io.jsonwebtoken.JwtBuilder b = Jwts.builder()
                .issuer(props.getIssuer())
                .subject(stableSubject)
                .claim("username", authentication.getName())
                .claim("authorities", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis));
        if (uid != null) {
            // Stored as a string so the gateway's
            // `c.get("uid", String.class)` works regardless of whether
            // the underlying id is Long, Integer, or already a String.
            b = b.claim("uid", uid.toString());
        }
        return b.signWith(key).compact();
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
