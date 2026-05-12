package com.spa.home_rental_application.api_gateway.api_gateway.filter;

import com.spa.home_rental_application.auth_commons.GatewaySigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancedExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit H3 (gateway side): enforces per-user "tokens issued before
 * this timestamp are dead" by calling auth-service's
 * {@code /auth/internal/tokens-revoked-before/{userId}} endpoint
 * before letting an inbound JWT through.
 *
 * <p>Hot-path concerns:
 *
 * <ul>
 *   <li><b>Latency.</b> A blocking call to auth-service for every
 *       request would 4-5× p50. We sit a {@link ConcurrentHashMap}
 *       in front with a 60-second TTL so post-logout propagation is
 *       at most 60s on cache-warm services. (Cold cache miss is a
 *       single Mono.fromCallable round-trip — already non-blocking
 *       under WebFlux.)</li>
 *   <li><b>Fail-open vs fail-closed.</b> If auth-service is
 *       unreachable, we <em>fail open</em>: log a warning, let the
 *       request through. Fail-closed would knock out the whole
 *       platform on an auth-service hiccup. The 5-minute JWT TTL
 *       caps the worst-case exposure.</li>
 *   <li><b>Loadbalancer.</b> We use {@code lb://HRA-auth-service}
 *       via the gateway's existing Eureka registration. No hardcoded
 *       host.</li>
 *   <li><b>Internal-auth HMAC.</b> The gateway already signs
 *       outbound requests with {@link GatewaySigner}; the auth-
 *       service's {@code GatewayAuthFilter} verifies, so this call
 *       is on the same trust plane as every other proxied request.</li>
 * </ul>
 *
 * <p>Enable / disable via {@code app.jwt.revoke-check.enabled}
 * (default true). Cache TTL via
 * {@code app.jwt.revoke-check.cache-ttl-seconds} (default 60).
 */
@Component
@Slf4j
public class TokenRevocationCheck {

    /** Sentinel: never revoked. -1 epoch-ms matches what auth-service returns. */
    private static final Instant NEVER = Instant.ofEpochMilli(-1L);

    private final WebClient webClient;
    private final GatewaySigner signer;
    private final boolean enabled;
    private final Duration cacheTtl;

    /** userId → (revokedBefore, cachedAt). Bounded by the active user count. */
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public TokenRevocationCheck(WebClient.Builder builder,
                                LoadBalancedExchangeFilterFunction lbFunction,
                                GatewaySigner signer,
                                @Value("${app.jwt.revoke-check.enabled:true}") boolean enabled,
                                @Value("${app.jwt.revoke-check.cache-ttl-seconds:60}") long ttlSec) {
        this.webClient = builder
                .baseUrl("lb://HRA-auth-service")
                .filter(lbFunction)
                .build();
        this.signer = signer;
        this.enabled = enabled;
        this.cacheTtl = Duration.ofSeconds(Math.max(5, ttlSec));
    }

    /**
     * Returns the user's tokens-revoked-before watermark (or
     * {@link #NEVER} if the user never logged out). On error the
     * fallback is also {@link #NEVER} (fail-open).
     */
    public Mono<Instant> revokedBefore(String userId) {
        if (!enabled || userId == null || userId.isBlank()) {
            return Mono.just(NEVER);
        }
        Cached hit = cache.get(userId);
        if (hit != null && !hit.isExpired(cacheTtl)) {
            return Mono.just(hit.value);
        }
        // Build the internal-auth signature for this GET hop. Auth-
        // service's GatewayAuthFilter verifies before serving.
        String path = "/auth/internal/tokens-revoked-before/" + userId;
        GatewaySigner.Signature sig = signer.sign("GET", path);

        return webClient.get()
                .uri(path)
                .header("X-Internal-Auth-Ts", String.valueOf(sig.timestamp()))
                .header("X-Internal-Auth-Sig", sig.signature())
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(2))
                .map(body -> {
                    Object epochMs = body == null ? null : body.get("epochMs");
                    long ms = (epochMs instanceof Number n) ? n.longValue() : -1L;
                    Instant resolved = ms < 0 ? NEVER : Instant.ofEpochMilli(ms);
                    cache.put(userId, new Cached(resolved, Instant.now()));
                    return resolved;
                })
                .onErrorResume(ex -> {
                    log.warn("TokenRevocationCheck lookup failed for userId={}: {} — failing open",
                            userId, ex.getMessage());
                    // Don't poison the cache with a "no-revocation" entry
                    // on failure — that would give an attacker holding a
                    // revoked token a 60s window after each failure to
                    // ride. Just return NEVER for this one request.
                    return Mono.just(NEVER);
                });
    }

    /** Test-only escape hatch: clear the cache (e.g. after rotating keys). */
    public void clearCache() {
        cache.clear();
    }

    private record Cached(Instant value, Instant cachedAt) {
        boolean isExpired(Duration ttl) {
            return Instant.now().isAfter(cachedAt.plus(ttl));
        }
    }
}
