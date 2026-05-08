package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Production KYC backend talking to Digio's REST API. Activates only when
 * {@code app.kyc.provider=DIGIO} so dev / test can stay on the mock.
 */
@Component
@ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "DIGIO")
@Slf4j
public class DigioKycProvider implements KycProvider {

    private final KycProperties props;
    private final RestTemplate http;

    public DigioKycProvider(KycProperties props, RestTemplate digioRestTemplate) {
        this.props = props;
        this.http = digioRestTemplate;
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "DIGIO")
    static class DigioHttpConfig {
        @Bean
        public RestTemplate digioRestTemplate(RestTemplateBuilder builder) {
            return builder
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(15))
                    .build();
        }
    }

    @Override
    public String name() {
        return "DIGIO";
    }

    @Override
    @CircuitBreaker(name = "digio-client", fallbackMethod = "initiateFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public InitiateResult initiate(String userId, InitiateKycRequest request) {
        String correlation = "KYC-" + UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "reference_id", correlation,
                "customer_identifier", userId,
                "callback_url", props.getDigio().getCallbackUrl()
        );
        log.info("→ Digio initiate userId={} correlation={}", userId, correlation);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getDigio().getBaseUrl() + "/v3/client/kyc/init",
                new HttpEntity<>(body, digioHeaders()),
                Map.class);
        if (resp == null) {
            throw new KycProviderException("Empty response from Digio initiate");
        }
        return new InitiateResult(
                correlation,
                String.valueOf(resp.getOrDefault("status", "PENDING")),
                String.valueOf(resp.getOrDefault("redirect_url", "")));
    }

    @SuppressWarnings("unused")
    private InitiateResult initiateFallback(String userId, InitiateKycRequest request, Throwable ex) {
        log.error("Digio initiate circuit open / failed for userId={}", userId, ex);
        throw new KycProviderException("Digio KYC initiation temporarily unavailable", ex);
    }

    @Override
    @CircuitBreaker(name = "digio-client", fallbackMethod = "panFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public PanResult verifyPan(String panNumber, String panHolderName) {
        Map<String, Object> body = Map.of(
                "id_no", panNumber,
                "name", panHolderName);
        log.info("→ Digio verifyPan pan=****{} ", panNumber.substring(panNumber.length() - 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getPan().getVerifyUrl(),
                new HttpEntity<>(body, digioHeaders()),
                Map.class);
        if (resp == null) {
            return new PanResult(false, null, "Empty response from Digio");
        }
        boolean valid = "VALID".equalsIgnoreCase(String.valueOf(resp.get("status")));
        return new PanResult(valid,
                String.valueOf(resp.getOrDefault("name", panHolderName)),
                valid ? null : String.valueOf(resp.getOrDefault("error", "PAN_INVALID")));
    }

    @SuppressWarnings("unused")
    private PanResult panFallback(String panNumber, String panHolderName, Throwable ex) {
        log.error("Digio PAN verify circuit open / failed", ex);
        return new PanResult(false, panHolderName, "PROVIDER_UNAVAILABLE");
    }

    private HttpHeaders digioHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Digio-Client-Id", props.getDigio().getClientId());
        h.setBearerAuth(props.getDigio().getApiKey());
        return h;
    }
}
