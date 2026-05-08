package com.spa.home_rental_application.kyc_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised KYC provider configuration. Bound from {@code app.kyc.*}.
 */
@ConfigurationProperties(prefix = "app.kyc")
@Getter
@Setter
public class KycProperties {

    /** Active provider: MOCK | DIGIO | SIGNZY. */
    private String provider = "MOCK";

    /** Per-environment salt for Aadhaar SHA-256 hashing. */
    private String aadhaarHashSalt;

    private Digio digio = new Digio();
    private Pan pan = new Pan();

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
}
