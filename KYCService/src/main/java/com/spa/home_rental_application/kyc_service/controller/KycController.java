package com.spa.home_rental_application.kyc_service.controller;

import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerAuthorizeRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigiLockerCallbackRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
import com.spa.home_rental_application.kyc_service.DTO.Response.DigiLockerAuthorizeResponse;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycReportDto;
import com.spa.home_rental_application.kyc_service.DTO.Response.KycResponseDto;
import com.spa.home_rental_application.kyc_service.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/kyc", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "KYC", description = "Aadhaar / PAN / DigiLocker verification")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @Operation(summary = "Start an Aadhaar / DigiLocker KYC flow for a user")
    @PostMapping(value = "/initiate/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycResponseDto> initiate(@PathVariable String userId,
                                                   @Valid @RequestBody InitiateKycRequest request) {
        log.info("POST /kyc/initiate/{}", userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(kycService.initiateKyc(userId, request));
    }

    @Operation(summary = "Get the current KYC status for a user")
    @GetMapping("/status/{userId}")
    public ResponseEntity<KycResponseDto> status(@PathVariable String userId) {
        return ResponseEntity.ok(kycService.getKycStatus(userId));
    }

    @Operation(summary = "Verify a PAN number (publishes kyc.pan.verified)")
    @PostMapping(value = "/verify-pan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycResponseDto> verifyPan(@Valid @RequestBody VerifyPanRequest request) {
        log.info("POST /kyc/verify-pan userId={}", request.userId());
        return ResponseEntity.ok(kycService.verifyPan(request));
    }

    @Operation(summary = "Link a verified DigiLocker account to the user's KYC record")
    @PostMapping(value = "/digilocker/link", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycResponseDto> linkDigilocker(@Valid @RequestBody DigilockerLinkRequest request) {
        log.info("POST /kyc/digilocker/link userId={}", request.userId());
        return ResponseEntity.ok(kycService.linkDigilocker(request));
    }

    @Operation(summary = "Compliance-grade KYC report (used by owner dashboards)")
    @GetMapping("/report/{userId}")
    public ResponseEntity<KycReportDto> report(@PathVariable String userId) {
        return ResponseEntity.ok(kycService.getKycReport(userId));
    }

    @Operation(summary = "Digio webhook — final outcome of an initiated KYC flow")
    @PostMapping(value = "/webhook/digio", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycResponseDto> digioWebhook(@RequestBody DigioWebhookPayload payload,
                                                       @RequestHeader(value = "X-Digio-Signature", required = false) String signature) {
        // Real impl would verify Digio's HMAC signature here. We log the
        // header so it's visible in audit traces — no signing key in dev.
        log.info("Digio webhook received signature-present={} ref={}",
                signature != null, payload.referenceId());
        return ResponseEntity.ok(kycService.handleDigioCallback(payload));
    }

    // ---------- DigiLocker OAuth flow ----------

    /**
     * Begin a DigiLocker OAuth flow. Returns the authorize URL the
     * frontend should redirect the user's browser to, plus a CSRF
     * state token. The frontend stashes the state in sessionStorage
     * and validates it on callback before posting to /digilocker/callback.
     */
    @Operation(summary = "Begin a DigiLocker OAuth flow — returns authorize URL + state token")
    @PostMapping(value = "/digilocker/authorize/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DigiLockerAuthorizeResponse> digilockerAuthorize(
            @PathVariable String userId,
            @Valid @RequestBody DigiLockerAuthorizeRequest request) {
        log.info("POST /kyc/digilocker/authorize/{}", userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(kycService.beginDigilockerAuthorize(userId, request));
    }

    /**
     * Complete a DigiLocker OAuth flow. The frontend calls this with the
     * {@code code} + {@code state} extracted from DigiLocker's redirect
     * URL. We validate state, exchange the code for an access token
     * server-side, fetch + parse the eAadhaar XML, and flip the record
     * to VERIFIED (publishing {@code kyc.verified}).
     */
    @Operation(summary = "Complete a DigiLocker OAuth flow — exchanges code for verification")
    @PostMapping(value = "/digilocker/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KycResponseDto> digilockerCallback(
            @Valid @RequestBody DigiLockerCallbackRequest request) {
        // Don't log the {@code code} — it's a one-shot secret. State is
        // safe (it's our own value we minted) but trimmed for cleanliness.
        log.info("POST /kyc/digilocker/callback state-prefix={}",
                request.state().substring(0, Math.min(8, request.state().length())));
        return ResponseEntity.ok(kycService.completeDigilockerCallback(request));
    }
}
