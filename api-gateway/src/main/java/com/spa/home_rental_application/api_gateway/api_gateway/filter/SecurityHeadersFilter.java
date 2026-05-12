package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Audit H6: stamp every response with the standard browser-hardening
 * headers. Spring Cloud Gateway runs on Netty (not the servlet stack)
 * so Spring Security's HSTS DSL doesn't apply — we add the headers
 * via a tiny GlobalFilter instead.
 *
 * <ul>
 *   <li>HSTS forces browsers to upgrade to HTTPS for a year and
 *       includes the preload directive so the apex domain is eligible
 *       for the Chrome preload list.</li>
 *   <li>X-Content-Type-Options: nosniff prevents MIME sniffing.</li>
 *   <li>X-Frame-Options: DENY blocks clickjacking iframes.</li>
 *   <li>Referrer-Policy: no-referrer keeps sensitive paths
 *       (password-reset tokens) out of upstream analytics referrers.</li>
 *   <li>Cross-Origin-Opener-Policy: same-origin isolates the window
 *       group from any popup or embedder, blocking the
 *       cross-origin-opener-side-channel class of attacks.</li>
 * </ul>
 *
 * <p>The HSTS header runs even when the connection is plain HTTP
 * (development). Browsers ignore it on insecure origins, so it's a
 * no-op in dev and active in prod — no profile branching needed.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    /** Run very late so we stamp the headers AFTER any other filter has set theirs. */
    public static final int ORDER = 10_000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders h = exchange.getResponse().getHeaders();
            // HttpHeaders has no setIfAbsent — use computeIfAbsent-style
            // guard so a downstream service that explicitly chose to
            // omit / customise a header wins.
            putIfAbsent(h, "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");
            putIfAbsent(h, "X-Content-Type-Options", "nosniff");
            putIfAbsent(h, "X-Frame-Options", "DENY");
            putIfAbsent(h, "Referrer-Policy", "no-referrer");
            putIfAbsent(h, "Cross-Origin-Opener-Policy", "same-origin");
            // We don't set a Content-Security-Policy here — the frontend
            // ships its own CSP via meta tags and ours would conflict
            // unless very carefully coordinated.
        }));
    }

    private static void putIfAbsent(HttpHeaders h, String name, String value) {
        if (!h.containsKey(name)) h.set(name, value);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
