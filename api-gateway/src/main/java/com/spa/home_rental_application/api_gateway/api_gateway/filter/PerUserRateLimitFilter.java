package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * P1-11: per-AUTHENTICATED-USER, per-route sliding-window rate limiter.
 *
 * <p>Complements {@link RateLimitFilter} which gates anonymous /
 * credential-attack surfaces per-IP. Per-IP alone is not enough for
 * authenticated endpoints: a single noisy user behind a corporate NAT
 * would burn the bucket for every coworker on the same egress IP. This
 * filter buckets by the gateway-stamped {@code X-Auth-User-Id} header
 * which {@link JWTAuthenticationFilter} sets at order -100, so by the
 * time we run at order -50 the header is populated for every
 * authenticated request.
 *
 * <p>Anonymous requests (no header) skip this filter — the per-IP
 * limiter already handled them. So this filter is a strict subset of
 * the existing pre-auth check, not a replacement.
 *
 * <p>Limits are intentionally generous because the per-IP filter is
 * the first line of defence; this is the second one targeted at the
 * "one logged-in user trying to scrape the catalog" attack profile,
 * not credential stuffing.
 */
@Component
@Slf4j
public class PerUserRateLimitFilter implements GlobalFilter, Ordered {

    /**
     * Run AFTER JWTAuthenticationFilter (-100) so the X-Auth-User-Id
     * header is set. Sitting at -50 puts us before
     * {@link GatewaySigningFilter} and the actual route dispatch.
     */
    public static final int ORDER = -50;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * Per-route limits, per authenticated user.
     *   /payments/{id}/initiate     :  20 / min   (no one legitimately
     *                                              initiates more than
     *                                              a couple of payments
     *                                              a minute)
     *   /properties/flats/near      :  60 / min   (geo query is
     *                                              expensive; honest
     *                                              users hit it a few
     *                                              times per session)
     *   /properties/buildings/search:  60 / min   (autocomplete-style
     *                                              endpoint)
     *   /properties/flats/**         : 300 / min   (browse / detail —
     *                                              generous default for
     *                                              normal SPA use)
     *   /notifications/**           : 300 / min   (bell + history)
     *   /documents/upload           :  30 / min   (uploading docs —
     *                                              expected to be rare)
     *   /**                         : 600 / min   (catch-all per-user
     *                                              ceiling; humanly
     *                                              impossible to hit
     *                                              from a real SPA)
     */
    private static final Map<String, int[]> RULES = new java.util.LinkedHashMap<>();
    static {
        RULES.put("/rentals/v1/payments/*/initiate", new int[]{20, 60});
        RULES.put("/rentals/v1/properties/flats/near", new int[]{60, 60});
        RULES.put("/rentals/v1/properties/buildings/search", new int[]{60, 60});
        RULES.put("/rentals/v1/documents/upload", new int[]{30, 60});
        RULES.put("/rentals/v1/properties/flats/**", new int[]{300, 60});
        RULES.put("/rentals/v1/notifications/**", new int[]{300, 60});
        RULES.put("/rentals/v1/**", new int[]{600, 60});
    }

    /**
     * {@code (userId + "|" + matchedRulePattern)} → recent-request
     * timestamps. The KEY uses the matched rule pattern (not the
     * concrete path) so e.g. every /rentals/v1/properties/flats/{id}
     * GET shares a single bucket — otherwise a user paginating
     * through flats would create a brand-new bucket per page and
     * effectively bypass the limit.
     */
    private final ConcurrentMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Anonymous request → skip. Per-IP filter handled it.
        String userId = exchange.getRequest().getHeaders().getFirst("X-Auth-User-Id");
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        // Skip actuator + swagger probes — operator traffic shouldn't be limited.
        if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        Map.Entry<String, int[]> matched = findRule(path);
        if (matched == null) return chain.filter(exchange);
        int[] rule = matched.getValue();

        String key = userId + "|" + matched.getKey();
        long now = System.currentTimeMillis();
        long windowMs = rule[1] * 1000L;

        Deque<Long> stamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (stamps) {
            while (!stamps.isEmpty() && now - stamps.peekFirst() > windowMs) {
                stamps.pollFirst();
            }
            if (stamps.size() >= rule[0]) {
                long retryAfterSec = Math.max(1, (windowMs - (now - stamps.peekFirst())) / 1000);
                log.warn("Per-user rate limit hit: userId={} ruleKey={} path={} hits={} window={}s",
                        userId, matched.getKey(), path, stamps.size(), rule[1]);
                return tooMany(exchange, retryAfterSec, rule[0], rule[1]);
            }
            stamps.addLast(now);
        }
        return chain.filter(exchange);
    }

    private static Map.Entry<String, int[]> findRule(String path) {
        // Order matters — the LinkedHashMap was initialised most-specific
        // first so the catch-all /** is matched last. First hit wins.
        for (Map.Entry<String, int[]> e : RULES.entrySet()) {
            if (MATCHER.match(e.getKey(), path)) return e;
        }
        return null;
    }

    private static Mono<Void> tooMany(ServerWebExchange exchange,
                                      long retryAfterSec, int max, int windowSec) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSec));
        String body = "{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\""
                + ",\"status\":429"
                + ",\"error\":\"Too Many Requests\""
                + ",\"message\":\"You're calling this endpoint too quickly — "
                + max + " requests per " + windowSec + "s. Try again in "
                + retryAfterSec + "s.\""
                + ",\"errorCode\":\"PER_USER_RATE_LIMIT_EXCEEDED\""
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
