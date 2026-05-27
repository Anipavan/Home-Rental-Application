package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * In-process KYC stub used in dev / CI when no real Digio creds are present.
 * Always returns success so engineers can exercise the happy path.
 */
@Component
@ConditionalOnProperty(prefix = "app.kyc", name = "provider", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockKycProvider implements KycProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public InitiateResult initiate(String userId, InitiateKycRequest request) {
        String ref = "MOCK-" + UUID.randomUUID();
        // Auto-verify for the mock provider so engineers / demo users see
        // the full happy-path end-to-end without waiting for a real Digio
        // webhook to fire. Production traffic uses DigioKycProvider which
        // returns PENDING and finalizes via webhook callback as designed.
        log.info("[MOCK-KYC] initiate userId={} ref={} -> auto-VERIFIED", userId, ref);
        return new InitiateResult(ref, "VERIFIED", "https://example.local/mock-kyc/" + ref);
    }

    @Override
    public PanResult verifyPan(String panNumber, String panHolderName, String dateOfBirth) {
        // Mock provider ignores DOB — it just stamps the request VALID
        // for any well-formed PAN. Logging the masked DOB so demos can
        // confirm the value is being threaded through the call chain.
        String maskedDob = dateOfBirth == null || dateOfBirth.length() < 4
                ? "(none)"
                : "****-**-" + dateOfBirth.substring(Math.max(0, dateOfBirth.length() - 2));
        log.info("[MOCK-KYC] verifyPan pan=****{} name={} dob={}",
                panNumber.substring(panNumber.length() - 2), panHolderName, maskedDob);
        return new PanResult(true, panHolderName, null);
    }
}
