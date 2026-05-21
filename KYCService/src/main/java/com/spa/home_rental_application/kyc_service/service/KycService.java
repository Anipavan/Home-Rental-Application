package com.spa.home_rental_application.kyc_service.service;

import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerAuthorizeRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerCallbackRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
import com.spa.home_rental_application.kyc_service.DTO.Response.DigiLockerAuthorizeResponse;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycReportDto;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycResponseDto;

public interface KycService {

    KycResponseDto initiateKyc(String userId, InitiateKycRequest request);

    KycResponseDto getKycStatus(String userId);

    KycResponseDto verifyPan(VerifyPanRequest request);

    KycResponseDto linkDigilocker(DigilockerLinkRequest request);

    KycReportDto getKycReport(String userId);

    KycResponseDto handleDigioCallback(DigioWebhookPayload payload);

    /** Idempotently creates a PENDING KYC stub when a user first registers. */
    void ensurePendingRecord(String userId);

    // ---------- DigiLocker OAuth flow ----------

    /**
     * Begins a DigiLocker OAuth flow for the given user. Generates a
     * CSRF-safe state token, persists it on the record with a TTL,
     * and returns the authorize URL the frontend should redirect the
     * browser to.
     */
    DigiLockerAuthorizeResponse beginDigilockerAuthorize(String userId, DigiLockerAuthorizeRequest request);

    /**
     * Handles the {@code code}+{@code state} the frontend hands us after
     * DigiLocker's redirect. Validates state, exchanges the code for an
     * access token, fetches the eAadhaar XML, parses it, and flips the
     * record to VERIFIED (publishing {@code kyc.verified} downstream).
     */
    KycResponseDto completeDigilockerCallback(DigiLockerCallbackRequest request);
}
