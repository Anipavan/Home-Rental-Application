package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;

/**
 * Strategy interface for pluggable KYC backends. Each provider (Digio,
 * Signzy, Mock) implements this and Spring picks the active one based on
 * {@code app.kyc.provider}.
 */
public interface KycProvider {

    /** Provider identifier — must be unique per implementation. */
    String name();

    /**
     * Initiates KYC with the upstream provider. Returns a reference id we
     * persist on our {@code kyc_records.kyc_reference_id} so the webhook
     * callback can resolve the user.
     */
    InitiateResult initiate(String userId, InitiateKycRequest request);

    /** Verifies a PAN against the provider. Returns true if PAN is valid. */
    PanResult verifyPan(String panNumber, String panHolderName);

    record InitiateResult(String referenceId, String providerStatus, String redirectUrl) {}

    record PanResult(boolean valid, String panHolderName, String failureReason) {}
}
