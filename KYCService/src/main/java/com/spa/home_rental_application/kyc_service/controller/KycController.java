package com.spa.home_rental_application.kyc_service.controller;

import com.spa.home_rental_application.kyc_service.DTO.Request.DigilockerLinkRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.DigioWebhookPayload;
import com.spa.home_rental_application.kyc_service.DTO.Request.InitiateKycRequest;
import com.spa.home_rental_application.kyc_service.DTO.Request.VerifyPanRequest;
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
}
