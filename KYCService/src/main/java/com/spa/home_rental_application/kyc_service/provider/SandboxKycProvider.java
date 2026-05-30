package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.Exceptionclass.KycProviderException;
import com.spa.home_rental_application.kyc_service.config.KycProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Sandbox.co.in (Quicko) KYC provider. Production-ready PAN verification
 * with a first-100-free signup tier — ideal for solo developers /
 * personal projects that need real KYC without becoming a DigiLocker
 * partner (which requires Pvt Ltd registration).
 *
 * <p>This impl supports {@link #verifyPan} only. PAN-only KYC is what
 * the Anirudh Homes flow uses today — Aadhaar verification will get
 * added later via Sandbox's Aadhaar Offline KYC endpoint (a different
 * call path with a user-uploaded ZIP).
 *
 * <p>{@link #initiate} returns a "INITIATED" status so the existing
 * controller path doesn't crash if someone calls /kyc/initiate against
 * a SANDBOX-configured service — but the real flow on the frontend
 * skips /initiate and goes straight to /verify-pan.
 *
 * <p>The Sandbox JWT (managed by {@link SandboxAuthClient}) is fetched
 * lazily on the first call and cached for ~10 min. On a 401 from
 * Sandbox we call {@code authClient.invalidate()} and retry once
 * with a fresh token — handles the rare server-side key-rotation case.
 *
 * <p>Activates only when {@code app.kyc.provider=SANDBOX}.
 */
@Component
@ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "SANDBOX")
@Slf4j
public class SandboxKycProvider implements KycProvider {

    /**
     * Name-match score threshold below which we treat the PAN as
     * "match failed" even though Sandbox itself returns VALID. Sandbox's
     * NSDL upstream returns a fuzzy match score 0-100; anything below
     * ~60 is essentially a different person with the same PAN. Tune
     * down for stricter matching, up for lenient.
     */
    private static final double NAME_MATCH_MIN_SCORE = 60.0;

    private final KycProperties props;
    private final RestTemplate http;
    private final SandboxAuthClient authClient;

    public SandboxKycProvider(KycProperties props,
                              RestTemplate sandboxRestTemplate,
                              SandboxAuthClient authClient) {
        this.props = props;
        this.http = sandboxRestTemplate;
        this.authClient = authClient;
    }

    @Override
    public String name() {
        return "SANDBOX";
    }

    /**
     * Sandbox's onboarding model has no separate "initiate" — you call
     * verify-pan directly. To stay compatible with the existing
     * KycService interface we return PENDING here, but the frontend
     * doesn't actually use this path for SANDBOX flows.
     */
    @Override
    public InitiateResult initiate(String userId, InitiateKycRequest request) {
        String ref = "SBX-" + java.util.UUID.randomUUID();
        log.info("SandboxKycProvider.initiate userId={} ref={} — PAN-only flow, no provider call yet", userId, ref);
        return new InitiateResult(ref, "PENDING", null);
    }

    /**
     * Verifies a PAN against Sandbox.co.in's PAN verification endpoint.
     * Wraps the call in {@code @CircuitBreaker} + {@code @Retryable} so
     * transient blips don't surface as user-facing errors.
     */
    @Override
    @CircuitBreaker(name = "sandbox-client", fallbackMethod = "panFallback")
    @Retryable(retryFor = RestClientException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public PanResult verifyPan(String panNumber, String panHolderName, String dateOfBirth) {
        log.info("→ Sandbox verifyPan pan=****{} ",
                panNumber.substring(panNumber.length() - 2));

        try {
            return doVerifyPan(panNumber, panHolderName, dateOfBirth);
        } catch (HttpClientErrorException.Unauthorized e) {
            // Token rotated server-side — invalidate cache + retry once
            // with a fresh JWT. A second 401 is genuinely an auth bug
            // (wrong api key/secret); let it bubble up.
            log.warn("Sandbox returned 401 — invalidating cached token and retrying once");
            authClient.invalidate();
            return doVerifyPan(panNumber, panHolderName, dateOfBirth);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            // 422 = USER DATA problem (PAN+DOB don't match NSDL, name
            // mismatch, malformed inputs). NOT a service-down condition,
            // so we MUST return a PanResult instead of re-throwing —
            // otherwise the circuit breaker treats every "user typed
            // wrong DOB" as a failed call and trips after a handful,
            // making the entire route 'temporarily unavailable' even
            // when Sandbox itself is perfectly healthy.
            //
            // Pull the actual {message} out of the Sandbox 422 body so
            // the user sees "date_of_birth doesn't match" instead of a
            // generic "service unavailable" and can actually fix the
            // input. Falls back to a sensible default if the body shape
            // ever changes.
            String reason = extractSandboxMessage(e.getResponseBodyAsString())
                    .orElse("Sandbox couldn't verify these details. Check your PAN, name and date of birth match exactly what's on your PAN card.");
            log.info("Sandbox 422 (data mismatch): {}", reason);
            return new PanResult(false, panHolderName, reason);
        } catch (HttpClientErrorException e) {
            // Other 4xx — 429 rate limit, 403 forbidden, etc. These are
            // also user-friendly failures (we tell them, they retry
            // later or contact support). Same reasoning as 422 —
            // returning a PanResult avoids needlessly tripping the
            // breaker on a non-outage condition.
            int code = e.getStatusCode().value();
            String reason = code == 429
                    ? "Verification is rate-limited right now. Please try again in a few minutes."
                    : extractSandboxMessage(e.getResponseBodyAsString())
                            .orElse("Verification couldn't complete (HTTP " + code + "). Please try again.");
            log.info("Sandbox {} (user-actionable): {}", code, reason);
            return new PanResult(false, panHolderName, reason);
        }
    }

    private PanResult doVerifyPan(String panNumber, String panHolderName, String dateOfBirth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", authClient.getAccessToken());
        headers.set("x-api-key", props.getSandbox().getApiKey());
        headers.set("x-api-version", props.getSandbox().getApiVersion());

        // Sandbox requires the holder's DOB as a second-factor identity
        // check — NSDL won't return a name match unless (PAN, DOB) belong
        // to the same person. Sandbox expects dd/MM/yyyy; we accept ISO
        // (yyyy-MM-dd) from the controller and convert here so the wire
        // shape is hidden from the rest of the codebase.
        String dobForSandbox = toSandboxDateFormat(dateOfBirth);

        // Sandbox accepts either a typed envelope or the bare fields.
        // We use the typed envelope per their current recommended schema
        // — gives them a stable hook to migrate the API later without
        // breaking older clients.
        String body = new JSONObject()
                .put("@entity", "in.co.sandbox.kyc.pan_verification.request")
                .put("pan", panNumber)
                .put("name_as_per_pan", panHolderName == null ? "" : panHolderName)
                .put("date_of_birth", dobForSandbox)
                .put("consent", "Y")
                .put("reason", "Rental platform KYC under DPDP Act 2023")
                .toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(
                props.getSandbox().getBaseUrl() + props.getSandbox().getPanVerifyPath(),
                new HttpEntity<>(body, headers),
                Map.class);

        if (resp == null) {
            throw new KycProviderException("Empty response from Sandbox /kyc/pan/verify");
        }

        // Top-level shape: {"code": 200, "transaction_id": "...", "data": {...}}.
        // The "data" map carries: pan, full_name, category, status,
        // name_match_score, name_match_result.
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() != 200) {
            String msg = String.valueOf(resp.getOrDefault("message", "Sandbox PAN verify failed"));
            log.info("Sandbox PAN verify code={} msg={}", n, msg);
            return new PanResult(false, panHolderName, msg);
        }

        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return new PanResult(false, panHolderName,
                    "Sandbox PAN verify returned no data");
        }

        String status = strOf(data.get("status"));            // "VALID" | "INVALID" | ...
        String fullName = strOf(data.get("full_name"));       // ALL CAPS per NSDL
        double matchScore = numOf(data.get("name_match_score"));
        String matchResult = strOf(data.get("name_match_result"));

        if (!"VALID".equalsIgnoreCase(status)) {
            log.info("Sandbox PAN verify status={} (treated as invalid)", status);
            return new PanResult(false, panHolderName,
                    "PAN status returned by NSDL: " + status);
        }

        // PAN exists at NSDL. Apply the local name-match threshold so an
        // attacker who knows the PAN but not the registered name can't
        // pass KYC by typing a random name.
        if (panHolderName != null && !panHolderName.isBlank()
                && matchScore < NAME_MATCH_MIN_SCORE) {
            log.info("Sandbox PAN verify VALID but name match score {} < {} — rejecting",
                    matchScore, NAME_MATCH_MIN_SCORE);
            return new PanResult(false,
                    fullName != null ? fullName : panHolderName,
                    "Name on PAN doesn't match what you entered "
                            + "(match score " + Math.round(matchScore) + "%, result " + matchResult + ")");
        }

        log.info("Sandbox PAN verify VALID name={} score={} result={}",
                fullName, matchScore, matchResult);
        return new PanResult(true, fullName, null);
    }

    @SuppressWarnings("unused")
    private PanResult panFallback(String panNumber, String panHolderName,
                                  String dateOfBirth, Throwable ex) {
        // Resilience4j matches fallback methods by signature — every
        // verifyPan parameter must appear here (in the same order) so
        // the circuit breaker can wire the fallback at startup.
        //
        // Discriminate by exception type so the user gets a useful
        // message instead of a one-size-fits-all "temporarily
        // unavailable". This fallback only fires for genuine
        // infra-level failures now (5xx, timeouts, connection refused,
        // breaker actually open) — 4xx body-shape errors are caught
        // and returned cleanly in verifyPan above without reaching us.
        log.error("Sandbox PAN verify fallback fired ({}): {}",
                ex.getClass().getSimpleName(), ex.getMessage());

        if (ex instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            return new PanResult(false, panHolderName,
                    "Verification is paused for a few seconds while we recover from a recent error. Please try again shortly.");
        }
        if (ex instanceof java.net.SocketTimeoutException
                || ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout")) {
            return new PanResult(false, panHolderName,
                    "NSDL is taking too long to respond. Please try again in a minute.");
        }
        if (ex instanceof RestClientException) {
            return new PanResult(false, panHolderName,
                    "Can't reach NSDL right now. Please try again in a minute.");
        }
        return new PanResult(false, panHolderName,
                "Verification didn't complete. Please try again.");
    }

    /**
     * Pull the {@code "message"} field out of a Sandbox JSON error body
     * so the user-facing failure text can be specific
     * ("date_of_birth doesn't match") instead of generic. Defensive —
     * returns Optional.empty() on any parse problem so a malformed
     * payload doesn't take down the whole error path.
     *
     * <p>Sandbox error shape:
     * <pre>
     *   { "code": 422, "timestamp": ..., "message": "...", "transaction_id": "..." }
     * </pre>
     */
    private static java.util.Optional<String> extractSandboxMessage(String body) {
        if (body == null || body.isBlank()) return java.util.Optional.empty();
        try {
            JSONObject obj = new JSONObject(body);
            String msg = obj.optString("message", null);
            return msg == null || msg.isBlank()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(msg);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    /* ---------- helpers ---------- */

    /**
     * Convert ISO {@code yyyy-MM-dd} (what the controller sends) to
     * {@code dd/MM/yyyy} (what Sandbox.co.in expects). Defensive: if the
     * input is null or already in the Sandbox format, hands it through
     * unchanged. A malformed input falls through to Sandbox unchanged
     * and lets Sandbox's 422 surface the user-facing error — better
     * than swallowing a typo here.
     */
    private static String toSandboxDateFormat(String iso) {
        if (iso == null || iso.isBlank()) return "";
        // Already dd/MM/yyyy? Pass through.
        if (iso.matches("^\\d{2}/\\d{2}/\\d{4}$")) return iso;
        // ISO yyyy-MM-dd?
        if (iso.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            String[] p = iso.split("-");
            return p[2] + "/" + p[1] + "/" + p[0];
        }
        // Unknown shape — pass through and let Sandbox reject.
        return iso;
    }

    private static String strOf(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double numOf(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
