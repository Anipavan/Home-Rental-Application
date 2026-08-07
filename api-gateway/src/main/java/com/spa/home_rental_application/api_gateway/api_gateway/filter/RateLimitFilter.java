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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP, per-route sliding-window rate limiter.
 *
 * <p>Buckets-per-route allow tighter limits on credential-attack
 * surfaces (login, register, forgot-password, bank-detail lookups)
 * while keeping bulk endpoints (/browse) free. Limits apply to
 * anonymous traffic too — the limiter runs BEFORE
 * {@link JWTAuthenticationFilter} so credential-stuffing doesn't get
 * to bypass the limiter by simply not presenting a token.
 *
 * <p>Both URL prefixes covered: {@code /rentals/v1/auth/*} AND the
 * legacy {@code /api/auth/*}. Earlier versions only rate-limited the
 * v1 form, and the legacy prefix (still served for backward compat)
 * was unlimited — trivially bypassable brute-force vector.
 *
 * <p>Implementation is in-memory (one process). For multi-instance
 * deployments behind a load balancer, switch to a Redis-backed
 * version — Spring Cloud Gateway ships {@code RedisRateLimiter} which
 * slots in via the {@code RequestRateLimiter} gateway filter. The
 * shape of the rules table here stays identical so the migration is
 * mechanical.
 *
 * <p>Routes not listed in {@link #RULES} pass through unlimited.
 * Limits are per-IP; the IP comes from the {@code X-Forwarded-For}
 * header parsed <b>right-to-left</b>, skipping any RFC1918 addresses
 * (Docker network + private LAN — trusted proxies). This prevents
 * clients from spoofing the header to get one-request-per-random-IP
 * bucketing, which defeated the older "leftmost-hop" parsing.
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
     * Per-route limits. Pattern -> (maxRequests, windowSeconds).
     * LinkedHashMap preserves insertion order so more-specific
     * patterns land before wildcards in the glob-fallback scan.
     *
     * <p>Every auth path is rate-limited under BOTH URL prefixes:
     * {@code /rentals/v1/auth/*} (canonical) and {@code /api/auth/*}
     * (legacy — same auth-service backend, so an unrated legacy
     * prefix was a full brute-force bypass).
     *
     * <p>Bank-account payout lookup is limited to 10/min per IP.
     * Every authenticated user CAN currently look up any other user's
     * payout details (the payment flow relies on it), which makes it
     * a mass-PII-exfiltration surface if unrated. Tenants pay their
     * own owner ~once a month; 10/min is well above legitimate use
     * but far below iteration speed for enumeration.
     */
    private static final Map<String, int[]> RULES;
    static {
        Map<String, int[]> r = new LinkedHashMap<>();

        // ── Credential-attack surfaces (both URL prefixes) ──
        r.put("/rentals/v1/auth/login",                     new int[]{10, 60});
        r.put("/api/auth/login",                            new int[]{10, 60});
        r.put("/rentals/v1/auth/register",                  new int[]{ 5, 60});
        r.put("/api/auth/register",                         new int[]{ 5, 60});
        r.put("/rentals/v1/auth/register/pending",          new int[]{ 5, 60});
        r.put("/api/auth/register/pending",                 new int[]{ 5, 60});
        r.put("/rentals/v1/auth/forgot-password",           new int[]{ 5, 60});
        r.put("/api/auth/forgot-password",                  new int[]{ 5, 60});
        r.put("/rentals/v1/auth/reset-password",            new int[]{10, 60});
        r.put("/api/auth/reset-password",                   new int[]{10, 60});
        r.put("/rentals/v1/auth/verify-email",              new int[]{10, 60});
        r.put("/api/auth/verify-email",                     new int[]{10, 60});
        r.put("/rentals/v1/auth/resend-verification",       new int[]{ 5, 60});
        r.put("/api/auth/resend-verification",              new int[]{ 5, 60});

        // ── PII-exfiltration surfaces ──
        // Bank/payout details: any signed-in user can currently look
        // up any other user's payout info (VPA, masked account,
        // bank + branch + IFSC). Rate-limit hard to prevent scraping
        // the whole owner base for UPI phishing.
        r.put("/rentals/v1/users/bank-accounts/payout/**",  new int[]{10, 60});
        r.put("/api/users/bank-accounts/payout/**",         new int[]{10, 60});

        // ── API doc enumeration (limit even though prod disables
        // springdoc; belt + braces in case one service is missed) ──
        r.put("/swagger-ui.html",                           new int[]{ 5, 60});
        r.put("/swagger-ui/**",                             new int[]{ 5, 60});
        r.put("/v3/api-docs/**",                            new int[]{ 5, 60});
        r.put("/aggregate/**",                              new int[]{ 5, 60});

        // ── Admin-only notification blaster ──
        r.put("/rentals/v1/notifications/send/**",          new int[]{30, 60});
        r.put("/api/notifications/send/**",                 new int[]{30, 60});

        RULES = Map.copyOf(r);
    }

    /**
     * {@code (ip + path)} -> recent-request timestamps. ArrayDeque
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

    /**
     * Resolve the caller's real IP, in priority order:
     *
     * <ol>
     *   <li>{@code CF-Connecting-IP} — Cloudflare stamps this per-
     *       request to the ORIGINAL client IP. Browsers can't spoof
     *       it because Cloudflare strips any inbound value. Only
     *       trusted when the immediate upstream is a private-network
     *       address (Caddy inside docker) — a request that bypassed
     *       our proxy chain can't be trusted to have set it honestly.
     *       Required when we sit behind Cloudflare because CF uses
     *       hundreds of edge IPs and each request may traverse a
     *       different one, defeating per-IP bucketing that keys on
     *       the Cloudflare edge.
     *   <li>{@code X-Forwarded-For} parsed <b>right-to-left</b>,
     *       skipping RFC1918 (private-network) addresses which are
     *       our own trusted proxies. Old leftmost-first parsing let
     *       an attacker spoof a new IP per request.
     *   <li>{@code RemoteAddress} — last resort.
     * </ol>
     */
    private static String clientIp(ServerHttpRequest req) {
        // 1) Cloudflare CF-Connecting-IP — only if request came from
        //    our trusted proxy chain (Caddy on docker network).
        String remoteAddr = req.getRemoteAddress() == null
                ? null
                : req.getRemoteAddress().getAddress().getHostAddress();
        if (remoteAddr != null && isTrustedProxy(remoteAddr)) {
            String cfIp = req.getHeaders().getFirst("CF-Connecting-IP");
            if (cfIp != null && !cfIp.isBlank()) {
                return cfIp.trim();
            }
        }
        // 2) XFF right-to-left, skipping trusted proxies.
        List<String> xff = req.getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String[] hops = String.join(",", xff).split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (hop.isEmpty()) continue;
                if (!isTrustedProxy(hop)) return hop;
            }
            String leftmost = hops[0].trim();
            if (!leftmost.isEmpty()) return leftmost;
        }
        // 3) Remote address.
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    /**
     * True if {@code ip} looks like a trusted proxy — RFC1918 private
     * addresses (Docker networks, LAN hops), loopback, or IPv6
     * link-local. Anything routable is NOT trusted.
     *
     * <p>Explicit CIDR ranges rather than {@code InetAddress.isSiteLocalAddress}
     * because that method has odd historical behaviour and doesn't
     * cover Docker's default 172.17-31 range consistently.
     */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        // IPv6 loopback + link-local
        if (ip.equals("::1") || ip.toLowerCase().startsWith("fe80:")) return true;
        // Strip IPv6 zone id if present
        int zoneIdx = ip.indexOf('%');
        if (zoneIdx >= 0) ip = ip.substring(0, zoneIdx);
        // IPv4 loopback
        if (ip.startsWith("127.")) return true;
        // RFC1918: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) { /* not an IPv4 literal */ }
            }
        }
        return false;
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
