package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.Exceptionclass.KycProviderException;
import com.spa.home_rental_application.kyc_service.config.KycProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the short-lived JWT that Sandbox.co.in's API gateway requires
 * on every business-endpoint call.
 *
 * <p>Sandbox's auth model:
 * <ol>
 *   <li>POST /authenticate with {@code x-api-key} + {@code x-api-secret}
 *       headers — yields an opaque JWT plus an {@code expires_in} hint
 *       (typically 600 seconds).</li>
 *   <li>Attach the JWT as the {@code Authorization} header on every
 *       subsequent call. Also re-send the {@code x-api-key} +
 *       {@code x-api-version} headers; the JWT alone isn't enough.</li>
 *   <li>JWT expires → 401 → fetch a fresh one.</li>
 * </ol>
 *
 * <p>This client caches the most-recently-issued JWT in memory and
 * refreshes when {@code expiresAt - safetySeconds < now}. {@code AtomicReference}
 * + lazy refresh on read keeps it thread-safe without a synchronized
 * block on every business call.
 *
 * <p>Activates only when {@code app.kyc.provider=SANDBOX} so the MOCK
 * default doesn't drag this bean in.
 */
@Component
@ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "SANDBOX")
@Slf4j
public class SandboxAuthClient {

    /**
     * Cached token. Volatile reads via AtomicReference give us safe
     * publication across threads without making every API call wait
     * on a mutex.
     */
    private final AtomicReference<CachedToken> cache = new AtomicReference<>(null);

    private final KycProperties props;
    private final RestTemplate http;

    public SandboxAuthClient(KycProperties props, RestTemplate sandboxRestTemplate) {
        this.props = props;
        this.http = sandboxRestTemplate;
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "SANDBOX")
    static class SandboxHttpConfig {
        /**
         * Dedicated RestTemplate so the Sandbox timeouts don't leak into
         * any other RestTemplate bean the service might inject elsewhere.
         */
        @Bean
        public RestTemplate sandboxRestTemplate(RestTemplateBuilder builder) {
            return builder
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(15))
                    .build();
        }
    }

    /**
     * Returns a valid JWT, fetching a fresh one if the cached value is
     * absent or about to expire. Safe to call from multiple threads.
     *
     * <p>The {@code @CircuitBreaker} guards the rare "Sandbox auth is
     * down" case — when the breaker is open, this fast-fails with a
     * {@link KycProviderException} instead of blocking every PAN-verify
     * call on a 15-second timeout.
     */
    @CircuitBreaker(name = "sandbox-client", fallbackMethod = "tokenFallback")
    public String getAccessToken() {
        CachedToken cached = cache.get();
        if (cached != null && Instant.now().isBefore(cached.refreshAt)) {
            return cached.accessToken;
        }
        log.info("Sandbox token cache miss — fetching new JWT");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("x-api-key", props.getSandbox().getApiKey());
        headers.set("x-api-secret", props.getSandbox().getApiSecret());
        headers.set("x-api-version", props.getSandbox().getApiVersion());

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> resp = http.postForObject(
                props.getSandbox().getBaseUrl() + props.getSandbox().getAuthPath(),
                new HttpEntity<>(new JSONObject().toString(), headers),
                java.util.Map.class);

        if (resp == null) {
            throw new KycProviderException("Empty response from Sandbox /authenticate");
        }
        // Sandbox wraps the response in {"code":200, "data": {...}, "message": "..."}.
        Object data = resp.get("data");
        String token = null;
        Long expiresIn = null;
        if (data instanceof java.util.Map<?, ?> dataMap) {
            Object accessTok = dataMap.get("access_token");
            if (accessTok != null) token = String.valueOf(accessTok);
            Object exp = dataMap.get("expires_in");
            if (exp instanceof Number n) expiresIn = n.longValue();
        }
        // Some Sandbox versions return access_token at the top level.
        if (token == null && resp.get("access_token") != null) {
            token = String.valueOf(resp.get("access_token"));
        }
        if (token == null || token.isBlank()) {
            // Don't log the response body verbatim — may contain a code that
            // a malicious actor could combine with leaked keys.
            log.warn("Sandbox /authenticate response missing access_token (keys={})", resp.keySet());
            throw new KycProviderException("Sandbox /authenticate returned no access_token");
        }

        long ttl = expiresIn != null ? expiresIn : 600L;            // default 10 min if absent
        long refreshIn = Math.max(30, ttl - props.getSandbox().getTokenRefreshSafetySeconds());
        Instant refreshAt = Instant.now().plusSeconds(refreshIn);
        cache.set(new CachedToken(token, refreshAt));

        log.info("Sandbox JWT refreshed (ttl={}s, refreshIn={}s)", ttl, refreshIn);
        return token;
    }

    /**
     * Force the next {@link #getAccessToken()} call to fetch a fresh JWT.
     * Called by business endpoints when they see a 401 — they may have
     * been holding a token that was revoked before its stated expiry
     * (e.g. Sandbox rotates keys server-side after a security event).
     */
    public void invalidate() {
        cache.set(null);
        log.info("Sandbox JWT cache invalidated — next call will refresh");
    }

    @SuppressWarnings("unused")
    private String tokenFallback(Throwable ex) {
        log.error("Sandbox /authenticate circuit open / failed", ex);
        throw new KycProviderException("Sandbox auth temporarily unavailable", ex);
    }

    /**
     * Immutable holder so the AtomicReference swap is a single atomic
     * operation — no chance of seeing token-without-refreshAt or
     * vice-versa under concurrent reads.
     */
    private record CachedToken(String accessToken, Instant refreshAt) {}
}
