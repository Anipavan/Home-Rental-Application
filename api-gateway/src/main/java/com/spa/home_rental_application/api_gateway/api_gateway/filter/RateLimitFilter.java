package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Audit H1: per-IP, per-route sliding-window rate limiter.
 *
 * <p>Buckets-per-route allow tighter limits on credential-attack
 * surfaces (login, forgot-password) while keeping bulk endpoints
 * (/browse) free. Limits apply to anonymous traffic too — the limiter
 * runs BEFORE {@link JWTAuthenticationFilter} so credential-stuffing
 * doesn't get to bypass the limiter by simply not presenting a token.
 *
 * <p>Implementation is in-memory (one process). For multi-instance
 * deployments behind a load balancer, switch to a Redis-backed
 * version — Spring Cloud Gateway ships
 * {@code RedisRateLimiter} which slots in via the
 * {@code RequestRateLimiter} gateway filter. The shape of the rules
 * table here stays identical so the migration is mechanical.
 *
 * <p>Routes not listed in {@link #RULES} pass through unlimited.
 * Limits are per-IP; the IP comes from the {@code X-Forwarded-For}
 * header (first hop) when present, else from the remote address.
 *
 * <p>On limit-exceeded, returns {@code 429 Too Many Requests} with a
 * structured JSON body the frontend can recognise and a
 * {@code Retry-After} header.
 */
@Component
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    /**
     * Run BEFORE JWTAuthenticationFilter so anonymous credential
     * stuffing is rate-limited too. JWT filter is at order -100; we
     * sit at -200.
     */
    public static final int ORDER = -200;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * Per-route limits. Pattern → (maxRequests, windowSeconds).
     *   /auth/login       :  10 attempts / min  / IP  (brute-force guard)
     *   /auth/register    :   5 / min            (signup spam guard)
     *   /auth/forgot-pwd  :   5 / min            (enumeration probing)
     *   /auth/reset-pwd   :  10 / min            (matched to forgot rate)
     *   /notifications/send/* : 30 / min          (admin tools — generous)
     */
    private static final Map<String, int[]> RULES = Map.of(
            "/rentals/v1/auth/login",            new int[]{10, 60},
            "/rentals/v1/auth/register",         new int[]{ 5, 60},
            "/rentals/v1/auth/forgot-password",  new int[]{ 5, 60},
            "/rentals/v1/auth/reset-password",   new int[]{10, 60},
            "/rentals/v1/notifications/send/**", new int[]{30, 60}
    );

    /**
     * {@code (ip + path)} → recent-request timestamps. ArrayDeque
     * holds longs (ms). On every request we drop entries older than
     * the window, then check size against the rule's max.
     *
     * <p>Memory: each entry is ~24 bytes. Per-IP cap implied by the
     * rule's max (e.g. 10 entries for login) keeps growth bounded.
     * Stale entries are GC'd on the next access — there's no periodic
     * sweep, which is fine at expected QPS but worth revisiting if
     * the gateway sees millions of unique IPs.
     */
    private final ConcurrentMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        int[] rule = findRule(path);
        if (rule == null) return chain.filter(exchange);

        String ip = clientIp(exchange.getRequest());
        String key = ip + "|" + path;
        long now = System.currentTimeMillis();
        long windowMs = rule[1] * 1000L;

        Deque<Long> stamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (stamps) {
            // Drop entries outside the current window.
            while (!stamps.isEmpty() && now - stamps.peekFirst() > windowMs) {
                stamps.pollFirst();
            }
            if (stamps.size() >= rule[0]) {
                long retryAfterSec = Math.max(1, (windowMs - (now - stamps.peekFirst())) / 1000);
                log.warn("Rate limit hit: ip={} path={} attempts={} window={}s",
                        ip, path, stamps.size(), rule[1]);
                return tooMany(exchange, retryAfterSec, rule[0], rule[1]);
            }
            stamps.addLast(now);
        }
        return chain.filter(exchange);
    }

    private static int[] findRule(String path) {
        // Exact paths first (cheap O(1) hash hit), then glob match the rest.
        int[] exact = RULES.get(path);
        if (exact != null) return exact;
        for (Map.Entry<String, int[]> e : RULES.entrySet()) {
            if (e.getKey().contains("*") && MATCHER.match(e.getKey(), path)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String clientIp(ServerHttpRequest req) {
        // Honour X-Forwarded-For but only take the LEFTMOST hop —
        // that's the original client. Subsequent commas are proxies.
        List<String> xff = req.getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && xff.get(0) != null && !xff.get(0).isBlank()) {
            return xff.get(0).split(",")[0].trim();
        }
        return req.getRemoteAddress() == null
                ? "unknown"
                : req.getRemoteAddress().getAddress().getHostAddress();
    }

    private static Mono<Void> tooMany(ServerWebExchange exchange,
                                      long retryAfterSec, int max, int windowSec) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSec));
        String body = "{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\""
                + ",\"status\":429"
                + ",\"error\":\"Too Many Requests\""
                + ",\"message\":\"Rate limit exceeded — " + max + " requests per "
                + windowSec + "s. Try again in " + retryAfterSec + "s.\""
                + ",\"errorCode\":\"RATE_LIMIT_EXCEEDED\""
                + ",\"path\":\"" + exchange.getRequest().getURI().getPath() + "\"}";
        DataBuffer buf = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
