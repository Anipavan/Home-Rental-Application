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

    public JWTAuthenticationFilter(JWTUtil jwtUtil, GatewayProperties gatewayProperties) {
        this.jwtUtil = jwtUtil;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicPath(path)) {
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
                String userName = c.getSubject();
                String userId   = Objects.toString(c.get("uid", String.class), "");
                String roles    = String.join(",", JWTUtil.extractAuthorities(c));

                ServerHttpRequest mutated = exchange.getRequest().mutate()
                        .headers(h -> {
                            // Force-set so any forged client header is clobbered.
                            h.set("X-Auth-User-Name", userName == null ? "" : userName);
                            h.set("X-Auth-User-Id", userId);
                            h.set("X-Auth-Roles", roles);
                        })
                        .build();
                yield chain.filter(exchange.mutate().request(mutated).build());
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

    private boolean isPublicPath(String path) {
        List<String> patterns = gatewayProperties.getPublicPaths();
        if (patterns == null) return false;
        for (String p : patterns) if (MATCHER.match(p, path)) return true;
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

    @Override
    public int getOrder() {
        return ORDER;
    }
}
