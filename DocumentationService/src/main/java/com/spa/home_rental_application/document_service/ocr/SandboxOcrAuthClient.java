package com.spa.home_rental_application.document_service.ocr;

import com.spa.home_rental_application.document_service.Exceptionclass.InvalidDocumentException;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches the short-lived JWT that Sandbox.co.in's API gateway requires on
 * every business call. Mirrors the KYC service's
 * {@code SandboxAuthClient} — same Sandbox account, same auth model,
 * but bound to this service's own {@link DocumentProperties.Sandbox} so
 * each service stays independently configurable.
 *
 * <p>Activates only when {@code app.documents.ocr.provider=SANDBOX} so
 * the STUB / TESSERACT paths don't pull this bean into their context.
 */
@Component
@ConditionalOnProperty(prefix = "app.documents.ocr", name = "provider", havingValue = "SANDBOX")
@Slf4j
public class SandboxOcrAuthClient {

    private final AtomicReference<CachedToken> cache = new AtomicReference<>(null);

    private final DocumentProperties props;
    private final RestTemplate http;

    public SandboxOcrAuthClient(DocumentProperties props,
                                RestTemplate sandboxOcrRestTemplate) {
        this.props = props;
        this.http = sandboxOcrRestTemplate;
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.documents.ocr", name = "provider", havingValue = "SANDBOX")
    static class HttpConfig {
        /**
         * Dedicated RestTemplate for OCR calls. 20s read timeout — OCR
         * inference can take 5-10s on the Sandbox backend for larger
         * Aadhaar images, so we give it more slack than the KYC PAN-verify
         * client (which is a simple directory lookup).
         */
        @Bean
        public RestTemplate sandboxOcrRestTemplate(RestTemplateBuilder builder) {
            return builder
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(20))
                    .build();
        }
    }

    /**
     * Returns a valid JWT, fetching a fresh one if the cached value is
     * absent or about to expire. Safe to call from multiple threads.
     */
    @CircuitBreaker(name = "sandbox-ocr-client", fallbackMethod = "tokenFallback")
    public String getAccessToken() {
        CachedToken cached = cache.get();
        if (cached != null && Instant.now().isBefore(cached.refreshAt)) {
            return cached.accessToken;
        }
        log.info("Sandbox OCR token cache miss — fetching new JWT");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("x-api-key", props.getSandbox().getApiKey());
        headers.set("x-api-secret", props.getSandbox().getApiSecret());
        headers.set("x-api-version", props.getSandbox().getApiVersion());

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getSandbox().getBaseUrl() + props.getSandbox().getAuthPath(),
                new HttpEntity<>(new JSONObject().toString(), headers),
                Map.class);

        if (resp == null) {
            throw new InvalidDocumentException("Empty response from Sandbox /authenticate");
        }
        String token = null;
        Long expiresIn = null;
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            if (dataMap.get("access_token") != null) token = String.valueOf(dataMap.get("access_token"));
            if (dataMap.get("expires_in") instanceof Number n) expiresIn = n.longValue();
        }
        if (token == null && resp.get("access_token") != null) {
            token = String.valueOf(resp.get("access_token"));
        }
        if (token == null || token.isBlank()) {
            log.warn("Sandbox /authenticate response missing access_token (keys={})", resp.keySet());
            throw new InvalidDocumentException("Sandbox /authenticate returned no access_token");
        }

        long ttl = expiresIn != null ? expiresIn : 600L;
        long refreshIn = Math.max(30, ttl - props.getSandbox().getTokenRefreshSafetySeconds());
        cache.set(new CachedToken(token, Instant.now().plusSeconds(refreshIn)));

        log.info("Sandbox OCR JWT refreshed (ttl={}s, refreshIn={}s)", ttl, refreshIn);
        return token;
    }

    /** Wipe the cache so the next call refreshes. Called on 401 responses. */
    public void invalidate() {
        cache.set(null);
    }

    @SuppressWarnings("unused")
    private String tokenFallback(Throwable ex) {
        log.error("Sandbox /authenticate circuit open / failed", ex);
        throw new InvalidDocumentException("Sandbox auth temporarily unavailable: " + ex.getMessage());
    }

    private record CachedToken(String accessToken, Instant refreshAt) {}
}
