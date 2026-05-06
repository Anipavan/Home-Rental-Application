package com.spa.home_rental_application.api_gateway.api_gateway.utils;

import com.spa.home_rental_application.api_gateway.api_gateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTUtilGatewayTest {

    private static final String SECRET =
            "U3VwZXJTZWNyZXRLZXlGb3JKV1RUb2tlbkdlbmVyYXRpb24xMjM0NTY3ODkwIQ==";

    private JWTUtil util() {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        p.setIssuer("home-rental-auth");
        return new JWTUtil(p);
    }

    @Test
    void okValidation_unwrapsClaims() {
        var auth = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT")));
        // Gateway-side JWTUtil only validates — doesn't have its own generate(); use direct jjwt.
        SecretKey k = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String token = Jwts.builder()
                .issuer("home-rental-auth")
                .subject("alice")
                .claim("authorities", List.of("ROLE_TENANT"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(k).compact();

        var v = util().validate(token);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Ok.class);
        var claims = ((JWTUtil.Validation.Ok) v).claims();
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(JWTUtil.extractAuthorities(claims)).contains("ROLE_TENANT");
    }

    @Test
    void issuerMismatch_marksInvalid() {
        SecretKey k = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String token = Jwts.builder()
                .issuer("evil-issuer")
                .subject("alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(k).compact();

        var v = util().validate(token);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Invalid.class);
    }

    @Test
    void expiredToken_marksExpired() {
        SecretKey k = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String token = Jwts.builder()
                .issuer("home-rental-auth")
                .subject("alice")
                .issuedAt(new Date(0))
                .expiration(new Date(0))
                .signWith(k).compact();
        var v = util().validate(token);
        assertThat(v).isInstanceOf(JWTUtil.Validation.Expired.class);
    }

    @Test
    void rejectsTooShortSecret() {
        JwtProperties p = new JwtProperties();
        p.setSecret("c2hvcnQ=");  // "short"
        assertThatThrownBy(() -> new JWTUtil(p))
                .isInstanceOf(IllegalStateException.class);
    }
}
