package com.spa.home_rental_application.auth_service.Utils;

import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTUtilTest {

    private static final String SECRET =
            "U3VwZXJTZWNyZXRLZXlGb3JKV1RUb2tlbkdlbmVyYXRpb24xMjM0NTY3ODkwIQ==";

    private JWTUtil util(long ttlSeconds) {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        p.setIssuer("home-rental-auth");
        p.setAccessTokenValiditySeconds(ttlSeconds);
        return new JWTUtil(p);
    }

    @Test
    void generate_andRoundTrip_subjectAndAuthorities() {
        var auth = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT"),
                        new SimpleGrantedAuthority("HRA_READ")));
        String token = util(60).generateToken(auth);
        var jws = util(60).parse(token);
        assertThat(jws.getPayload().getSubject()).isEqualTo("alice");
        assertThat(jws.getPayload().getIssuer()).isEqualTo("home-rental-auth");
        assertThat(JWTUtil.extractAuthorities(jws.getPayload()))
                .containsExactlyInAnyOrder("ROLE_TENANT", "HRA_READ");
    }

    @Test
    void rejectsTooShortSecret() {
        JwtProperties p = new JwtProperties();
        p.setSecret("c2hvcnQ=");  // base64 of "short"
        assertThatThrownBy(() -> new JWTUtil(p))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detectsExpired() throws InterruptedException {
        // Manually mint an expired token signed with the same secret.
        byte[] bytes = Base64.getDecoder().decode(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(bytes);
        String expired = Jwts.builder()
                .issuer("home-rental-auth")
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key).compact();
        var v = util(3600).validate(expired);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Expired.class);
    }

    @Test
    void detectsTampered() {
        var auth = new UsernamePasswordAuthenticationToken("alice", null, List.of());
        String token = util(3600).generateToken(auth);
        // Flip one char in the signature
        String tampered = token.substring(0, token.length() - 2) + "AA";
        var v = util(3600).validate(tampered);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Invalid.class);
    }

    @Test
    void detectsIssuerMismatch() {
        byte[] bytes = SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String wrongIssuer = Jwts.builder()
                .issuer("evil-issuer")
                .subject("alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        var v = util(3600).validate(wrongIssuer);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Invalid.class);
    }

    @Test
    void parseExpired_throwsExpiredJwtException() {
        byte[] bytes = Base64.getDecoder().decode(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(bytes);
        String expired = Jwts.builder()
                .issuer("home-rental-auth")
                .subject("a").issuedAt(new Date(0))
                .expiration(new Date(0))
                .signWith(key).compact();
        assertThatThrownBy(() -> util(3600).parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
