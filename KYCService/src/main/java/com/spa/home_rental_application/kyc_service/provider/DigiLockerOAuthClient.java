package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.Exceptionclass.KycProviderException;
import com.spa.home_rental_application.kyc_service.config.KycProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Thin REST client over the two DigiLocker server-side endpoints we hit
 * during a KYC flow:
 *
 * <ol>
 *   <li>{@link #exchangeCodeForToken(String)} — POST {@code /oauth2/1/token}
 *       with the {@code code} from the redirect callback in exchange for
 *       an OAuth 2.0 {@code access_token}. Uses HTTP Basic auth
 *       (clientId:clientSecret) per RFC 6749 §2.3.1.</li>
 *   <li>{@link #fetchEAadhaarXml(String)} — GET {@code /oauth2/3/xml/eaadhaar}
 *       with {@code Authorization: Bearer <token>} to retrieve the signed
 *       Aadhaar XML. The body is raw XML (not JSON-wrapped).</li>
 * </ol>
 *
 * <p>Both calls are wrapped by Resilience4j {@code @CircuitBreaker} and
 * {@code @Retryable} so an upstream brown-out doesn't pin every KYC
 * request to a 15-second timeout — fast-fail when the breaker is open.
 *
 * <p>The access_token is short-lived (default ~10 minutes per the spec)
 * and is <b>never persisted</b> — it lives only as a local variable in
 * the calling service method and is discarded once the eAadhaar XML
 * has been parsed.
 *
 * <p>Activates only when {@code app.kyc.provider=DIGILOCKER}, so the
 * dev/CI {@code MOCK} default doesn't pull this bean in.
 */
@Component
@ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "DIGILOCKER")
@Slf4j
public class DigiLockerOAuthClient {

    private final KycProperties props;
    private final RestTemplate http;

    public DigiLockerOAuthClient(KycProperties props,
                                  RestTemplate digilockerRestTemplate) {
        this.props = props;
        this.http = digilockerRestTemplate;
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "DIGILOCKER")
    static class DigilockerHttpConfig {
        /**
         * Dedicated RestTemplate so the timeouts apply only to DigiLocker
         * calls — won't affect any other Spring-managed RestTemplate the
         * service might inject elsewhere.
         */
        @Bean
        public RestTemplate digilockerRestTemplate(RestTemplateBuilder builder) {
            return builder
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(15))
                    .build();
        }
    }

    /**
     * Exchanges an OAuth 2.0 {@code code} (from the redirect callback) for
     * an access token. Returns the raw token string.
     *
     * <p>DigiLocker's token endpoint returns JSON of the shape
     * {@code {"access_token":"…","expires_in":600,"token_type":"Bearer","refresh_token":"…"}}.
     * We only care about the {@code access_token} — refresh isn't useful
     * because we hit the API exactly once per KYC and the resulting
     * eAadhaar payload is what we persist (as a hash + last4).
     */
    @CircuitBreaker(name = "digilocker-client", fallbackMethod = "tokenFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public String exchangeCodeForToken(String code) {
        log.info("→ DigiLocker token exchange (code prefix={})",
                code == null ? "null" : code.substring(0, Math.min(6, code.length())));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        // RFC 6749 §2.3.1 — confidential client authenticates via HTTP Basic
        // with the client_id:client_secret. Keeps the secret out of the body.
        headers.setBasicAuth(
                props.getDigilocker().getClientId(),
                props.getDigilocker().getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", props.getDigilocker().getRedirectUri());
        body.add("client_id", props.getDigilocker().getClientId());

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getDigilocker().getTokenUrl(),
                new HttpEntity<>(body, headers),
                Map.class);

        if (resp == null) {
            throw new KycProviderException("Empty response from DigiLocker token endpoint");
        }
        Object token = resp.get("access_token");
        if (token == null || String.valueOf(token).isBlank()) {
            // Don't echo the full response — it may carry implementation
            // details we don't want in audit logs. Mark the error code so
            // KycServiceImpl can map it to a clean user-facing reason.
            log.warn("DigiLocker token response missing access_token (keys={})", resp.keySet());
            throw new KycProviderException("DigiLocker token response missing access_token");
        }
        log.info("← DigiLocker token received (expires_in={})", resp.get("expires_in"));
        return String.valueOf(token);
    }

    /**
     * Fetches the signed eAadhaar XML document for the user behind the
     * given access_token. Returns the raw XML body — parsing is the
     * caller's responsibility (see {@link EAadhaarXmlParser}).
     */
    @CircuitBreaker(name = "digilocker-client", fallbackMethod = "xmlFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public String fetchEAadhaarXml(String accessToken) {
        log.info("→ DigiLocker fetch eAadhaar XML");

        HttpHeaders headers = new HttpHeaders();
        // DigiLocker returns raw XML (not application/json) — set Accept
        // accordingly so any reverse proxy in front of MeitY doesn't
        // helpfully content-negotiate it into HTML.
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        headers.setBearerAuth(accessToken);

        String xml = http.exchange(
                props.getDigilocker().getEaadhaarUrl(),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class).getBody();

        if (xml == null || xml.isBlank()) {
            throw new KycProviderException("Empty eAadhaar XML body from DigiLocker");
        }
        log.info("← DigiLocker eAadhaar XML received (bytes={})", xml.length());
        return xml;
    }

    // ---------- Circuit-breaker fallbacks ----------

    @SuppressWarnings("unused")
    private String tokenFallback(String code, Throwable ex) {
        log.error("DigiLocker token exchange circuit open / failed", ex);
        throw new KycProviderException("DigiLocker token exchange temporarily unavailable", ex);
    }

    @SuppressWarnings("unused")
    private String xmlFallback(String accessToken, Throwable ex) {
        log.error("DigiLocker eAadhaar fetch circuit open / failed", ex);
        throw new KycProviderException("DigiLocker eAadhaar fetch temporarily unavailable", ex);
    }
}
