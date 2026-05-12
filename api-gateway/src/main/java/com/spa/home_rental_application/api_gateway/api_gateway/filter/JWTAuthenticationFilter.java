package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import com.spa.home_rental_application.api_gateway.api_gateway.config.GatewayProperties;
import com.spa.home_rental_application.api_gateway.api_gateway.utils.JWTUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on protected
 * routes and stamps the request with {@code X-Auth-User-Name} /
 * {@code X-Auth-User-Id} / {@code X-Auth-Roles} headers that downstream
 * services consume.
 *
 * <p>Public paths (login, register, refresh, forgot-password,
 * reset-password, swagger, actuator, circuit-breaker fallback) bypass this
 * filter — they're listed in {@code app.gateway.public-paths}.
 *
 * <p>On expiry the filter responds {@code 401} with
 * {@code X-Token-Expired: true}, signalling the client to call
 * {@code POST /rentals/v1/auth/refresh} with its refresh token.
 */
@Component
@Slf4j
public class JWTAuthenticationFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -100;
    private static final String BEARER = "Bearer ";
    private static final PathMatcher MATCHER = new AntPathMatcher();

    private final JWTUtil jwtUtil;
    private final GatewayProperties gatewayProperties;
    private final TokenRevocationCheck revocationCheck;

    public JWTAuthenticationFilter(JWTUtil jwtUtil,
                                   GatewayProperties gatewayProperties,
                                   TokenRevocationCheck revocationCheck) {
        this.jwtUtil = jwtUtil;
        this.gatewayProperties = gatewayProperties;
        this.revocationCheck = revocationCheck;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod() == null
                ? ""
                : exchange.getRequest().getMethod().name();
        if (isPublicPath(path, method)) {
            // Strip any client-supplied X-Auth-* headers — we don't want spoofing
            ServerHttpRequest scrubbed = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-Auth-User-Name");
                        h.remove("X-Auth-User-Id");
                        h.remove("X-Auth-Roles");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(scrubbed).build());
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header",
                    "AUTH_REQUIRED",
                    "X-Auth-Required", "true");
        }

        String token = authHeader.substring(BEARER.length()).trim();
        JWTUtil.Validation v = jwtUtil.validate(token);

        return switch (v) {
            case JWTUtil.Validation.Ok ok -> {
                Claims c = ok.claims();
                // Audit M5: tokens issued by the post-MEDIUM auth-service
                // put the stable uid in `sub` and the display username in
                // a separate `username` claim. Tokens from older builds
                // (pre-MEDIUM-Phase-A) carry username in `sub`. Prefer
                // the new claim with a getSubject() fallback so we don't
                // break in-flight tokens during the rollover window.
                String fromClaim = c.get("username", String.class);
                final String userName = sanitizeHeader(
                        (fromClaim != null && !fromClaim.isBlank()) ? fromClaim : c.getSubject());
                // Audit L6: sanitize claim values before stamping onto
                // downstream headers (drops CR/LF + non-printable
                // chars, caps length at 200).
                final String userId   = sanitizeHeader(Objects.toString(c.get("uid", String.class), ""));
                final String roles    = sanitizeHeader(String.join(",", JWTUtil.extractAuthorities(c)));
                // Audit H3 (gateway side): refuse if the JWT's iat is
                // older than the user's tokens-revoked-before
                // watermark. Server-side foundation (auth-service
                // bump + endpoint) shipped in HIGH Phase 1; the
                // gateway enforcement is the closing piece. We do the
                // lookup async via WebClient + cache so the hot path
                // stays non-blocking under WebFlux.
                final java.util.Date iat = c.getIssuedAt();
                yield revocationCheck.revokedBefore(userId)
                        .flatMap(watermark -> {
                            if (iat != null && watermark != null
                                    && iat.toInstant().isBefore(watermark)) {
                                log.info("JWT rejected — iat {} predates revoke watermark {} for uid={}",
                                        iat.toInstant(), watermark, userId);
                                return reject(exchange, HttpStatus.UNAUTHORIZED,
                                        "Session was logged out. Please sign in again.",
                                        "TOKEN_REVOKED",
                                        "X-Token-Revoked", "true");
                            }
                            ServerHttpRequest mutated = exchange.getRequest().mutate()
                                    .headers(h -> {
                                        // Force-set so any forged client header is clobbered.
                                        h.set("X-Auth-User-Name", userName == null ? "" : userName);
                                        h.set("X-Auth-User-Id", userId);
                                        h.set("X-Auth-Roles", roles);
                                    })
                                    .build();
                            return chain.filter(exchange.mutate().request(mutated).build());
                        });
            }
            case JWTUtil.Validation.Expired exp -> {
                log.info("JWT expired on {}: {}", path, exp.message());
                yield reject(exchange, HttpStatus.UNAUTHORIZED,
                        "Access token has expired. Call POST /rentals/v1/auth/refresh with your refresh token to obtain a new one.",
                        "TOKEN_EXPIRED",
                        "X-Token-Expired", "true");
            }
            case JWTUtil.Validation.Invalid bad -> {
                log.warn("JWT rejected on {}: {}", path, bad.message());
                yield reject(exchange, HttpStatus.UNAUTHORIZED,
                        "Invalid access token. Please log in again.",
                        "TOKEN_INVALID",
                        "X-Token-Invalid", "true");
            }
        };
    }

    /**
     * Public-path patterns can optionally be method-scoped:
     *   "GET /rentals/v1/properties/flats/**"     → only anonymous GETs allowed
     *   "/rentals/v1/auth/login"                  → any method (backwards-compatible)
     *
     * Method-scoping is critical for routes where READS are public but
     * WRITES must still be authenticated (e.g. property listings —
     * anyone can browse, only the owner can edit / delete). Without
     * the method check, opening `/rentals/v1/properties/**` to
     * anonymous traffic would let unauthenticated DELETE through too.
     */
    private boolean isPublicPath(String path, String method) {
        List<String> patterns = gatewayProperties.getPublicPaths();
        if (patterns == null) return false;
        for (String p : patterns) {
            // Strip optional leading "METHOD " prefix.
            int sp = p.indexOf(' ');
            if (sp > 0 && sp < 8) {
                String patMethod = p.substring(0, sp).trim().toUpperCase();
                String patPath = p.substring(sp + 1).trim();
                if (patMethod.equals(method) && MATCHER.match(patPath, path)) {
                    return true;
                }
            } else {
                // Unprefixed pattern: applies to every method (legacy behaviour).
                if (MATCHER.match(p, path)) return true;
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status,
                              String message, String errorCode,
                              String hintHeader, String hintValue) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add(hintHeader, hintValue);
        String body = "{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\""
                + ",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\""
                + ",\"message\":\"" + escape(message) + "\""
                + ",\"errorCode\":\"" + errorCode + "\""
                + ",\"path\":\"" + exchange.getRequest().getURI().getPath() + "\"}";
        DataBuffer buf = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Audit L6: drop control chars + anything outside the printable
     * ASCII range before using a JWT claim as an HTTP header value.
     * Reactor-Netty would itself reject a header containing CR/LF on
     * write (with a 500) — sanitizing quietly here avoids surfacing
     * that as a server error to the client when the cause is just a
     * malformed token. Length-capped at 200 so a pathological claim
     * can't bloat the header table.
     */
    private static String sanitizeHeader(String s) {
        if (s == null || s.isEmpty()) return "";
        int n = Math.min(s.length(), 200);
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7F) b.append(c);     // printable ASCII only
        }
        return b.toString();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
