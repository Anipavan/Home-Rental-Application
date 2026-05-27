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

    /**
     * Verifies a PAN against the provider.
     *
     * @param panNumber    the 10-character PAN (AAAAA9999A)
     * @param panHolderName the name as printed on the card
     * @param dateOfBirth   the holder's DOB in ISO {@code yyyy-MM-dd} format.
     *                      Required by Sandbox.co.in (and most NSDL upstreams)
     *                      as a second-factor match — the provider passes
     *                      (PAN + DOB) to NSDL and the call fails if they
     *                      don't both belong to the same person on file.
     *                      Mock / Digio / DigiLocker implementations may
     *                      ignore this; SandboxKycProvider sends it.
     * @return PanResult — valid flag, holder name from NSDL, and (if invalid)
     *                     a human-readable failure reason for the user toast.
     */
    PanResult verifyPan(String panNumber, String panHolderName, String dateOfBirth);

    record InitiateResult(String referenceId, String providerStatus, String redirectUrl) {}

    record PanResult(boolean valid, String panHolderName, String failureReason) {}
}
