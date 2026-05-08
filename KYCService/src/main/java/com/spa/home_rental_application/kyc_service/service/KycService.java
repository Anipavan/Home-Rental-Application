package com.spa.home_rental_application.kyc_service.service;

import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
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
}
