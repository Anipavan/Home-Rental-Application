package com.spa.home_rental_application.kyc_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised KYC provider configuration. Bound from {@code app.kyc.*}.
 *
 * <p>The {@link #provider} field is the single switch that picks which
 * {@link com.spa.home_rental_application.kyc_service.provider.KycProvider}
 * implementation Spring activates — see the {@code @ConditionalOnProperty}
 * on each provider class:
 * <ul>
 *   <li>{@code MOCK} → MockKycProvider (default, dev/CI)</li>
 *   <li>{@code DIGIO} → DigioKycProvider (legacy)</li>
 *   <li>{@code DIGILOCKER} → DigiLockerProvider (MeitY OAuth flow)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.kyc")
@Getter
@Setter
public class KycProperties {

    /** Active provider: MOCK | DIGIO | DIGILOCKER | SANDBOX | SIGNZY. */
    private String provider = "MOCK";

    /** Per-environment salt for Aadhaar SHA-256 hashing. Never persisted in plain text. */
    private String aadhaarHashSalt;

    /**
     * When true, a successful PAN-verify call flips the entire KYC record
     * to VERIFIED + publishes {@code kyc.verified}. Use this for PAN-only
     * KYC flows (e.g. Sandbox.co.in) where Aadhaar isn't part of the
     * scope. When false (default) PAN verify only sets
     * {@code panVerified=true} and the record stays INITIATED until a
     * separate Aadhaar step completes.
     */
    private boolean panOnlyKyc = false;

    private Digio digio = new Digio();
    private Pan pan = new Pan();
    private Digilocker digilocker = new Digilocker();
    private Sandbox sandbox = new Sandbox();

    @Getter @Setter
    public static class Digio {
        private String baseUrl;
        private String apiKey;
        private String clientId;
        private String callbackUrl;
    }

    @Getter @Setter
    public static class Pan {
        private String verifyUrl;
    }

    /**
     * DigiLocker (MeitY) OAuth 2.0 + eAadhaar config.
     *
     * <p>Three URLs MeitY publishes on the DigiLocker partner portal:
     * <ul>
     *   <li>{@code authorizeUrl} — where we send the user's browser to log in
     *       and grant consent. Typically {@code https://api.digitallocker.gov.in/public/oauth2/1/authorize}.</li>
     *   <li>{@code tokenUrl} — server-side endpoint we POST the auth code to
     *       in exchange for an access_token. Typically {@code .../oauth2/1/token}.</li>
     *   <li>{@code eaadhaarUrl} — server-side endpoint we GET with the
     *       access_token to retrieve the signed Aadhaar XML document.
     *       Typically {@code .../public/oauth2/3/xml/eaadhaar}.</li>
     * </ul>
     *
     * <p>{@code redirectUri} is the absolute URL DigiLocker will 302 the
     * user back to once they grant consent — must match exactly what's
     * registered in the partner portal. We point it at a frontend route
     * ({@code https://anirudhhomes.in/app/kyc/callback}); the frontend
     * then hands the {@code code}+{@code state} back to our backend via
     * an authenticated XHR, keeping the {@code clientSecret} server-side.
     *
     * <p>{@code stateTtlSeconds} guards the CSRF state token — anything
     * older than this many seconds is rejected on callback. Default 10
     * minutes mirrors the OAuth 2.0 RFC 6749 §10.12 recommendation.
     */
    /**
     * Sandbox.co.in (Quicko) PAN / Aadhaar Offline KYC provider config.
     *
     * <p>Their API uses a two-step auth: first POST credentials to
     * {@code /authenticate} to get a short-lived JWT, then attach the
     * JWT to every business endpoint. The JWT expires after ~10 minutes
     * so {@link com.spa.home_rental_application.kyc_service.provider.SandboxAuthClient}
     * caches it and refreshes on demand.
     *
     * <p>Pricing tier (as of 2026): first 100 PAN verifications free
     * on signup, ~₹0.50/call after. Aadhaar Offline KYC ~₹2-5/call.
     * Production keys require a ₹500 minimum wallet top-up.
     *
     * <p>Solo developers can sign up with personal PAN + email — no
     * incorporation required. This is the path for personal projects
     * that need real KYC without becoming a DigiLocker partner.
     */
    @Getter @Setter
    public static class Sandbox {
        /** Base URL — usually {@code https://api.sandbox.co.in}. */
        private String baseUrl = "https://api.sandbox.co.in";
        /** API key from the Sandbox dashboard. Treat as a secret. */
        private String apiKey;
        /** API secret. Pairs with apiKey for the /authenticate call. */
        private String apiSecret;
        /** API version header — defaults to "1.0" which is what Sandbox uses today. */
        private String apiVersion = "1.0";
        /** Stable PAN-verify endpoint path. Override only if Sandbox renames it. */
        private String panVerifyPath = "/kyc/pan/verify";
        /** /authenticate path — yields a short-lived JWT. */
        private String authPath = "/authenticate";
        /** Cache the JWT this many seconds shy of its real expiry (safety margin). */
        private int tokenRefreshSafetySeconds = 60;
    }

    @Getter @Setter
    public static class Digilocker {
        private String authorizeUrl;
        private String tokenUrl;
        private String eaadhaarUrl;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        /** TTL for the OAuth state token in seconds. Default 10 minutes. */
        private int stateTtlSeconds = 600;
    }
}
