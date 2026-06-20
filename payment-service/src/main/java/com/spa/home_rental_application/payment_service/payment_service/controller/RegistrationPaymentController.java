package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.CreateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiateRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyRegistrationPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.CreateRegistrationPaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InitiatePaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.RegistrationPaymentResultResponse;
import com.spa.home_rental_application.payment_service.payment_service.security.RegistrationPaymentTokenVerifier;
import com.spa.home_rental_application.payment_service.payment_service.service.RegistrationPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Paid maintainer-signup endpoints. Sits next to
 * {@link PaymentGatewayController} but has its own URL prefix
 * ({@code /payments/registration/**}) so the api-gateway can
 * whitelist these three paths from JWT auth without weakening the
 * existing payment routes.
 *
 * <ul>
 *   <li>{@code POST /payments/registration/create-pending} — internal,
 *       auth-service Feigns into it during {@code /auth/register/pending}.
 *       Gateway-public, gateway-HMAC required (the FeignGatewaySigningInterceptor
 *       on auth-service signs the call).</li>
 *   <li>{@code POST /payments/registration/initiate} — REG_PAY-token-gated.
 *       Frontend hits it from the paywall page.</li>
 *   <li>{@code POST /payments/registration/verify} — REG_PAY-token-gated.
 *       Frontend hits it on the Razorpay success callback.</li>
 * </ul>
 *
 * <p>The REG_PAY token is parsed + verified inline (not via a Spring
 * Security filter) so we don't open the rest of payment-service to
 * REG_PAY tokens — those should ONLY work on the two routes that
 * explicitly call {@link RegistrationPaymentTokenVerifier#verifyForPayment}.
 */
@RestController
@RequestMapping(value = "/payments/registration", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Registration Payment",
        description = "Paid maintainer-signup flow (pending creation, Razorpay initiate, verify)")
public class RegistrationPaymentController {

    private final RegistrationPaymentService registrationService;
    private final RegistrationPaymentTokenVerifier tokenVerifier;

    public RegistrationPaymentController(RegistrationPaymentService registrationService,
                                          RegistrationPaymentTokenVerifier tokenVerifier) {
        this.registrationService = registrationService;
        this.tokenVerifier = tokenVerifier;
    }

    @Operation(summary = "Create a PENDING registration-fee payment (internal — called by auth-service Feign).")
    @PostMapping(value = "/create-pending", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateRegistrationPaymentResponse> createPending(
            @Valid @RequestBody CreateRegistrationPaymentRequest req) {
        log.info("POST /payments/registration/create-pending payerAuthUserId={} amountInr={}",
                req.payerAuthUserId(), req.amountInr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.createPending(req));
    }

    @Operation(summary = "Initiate the Razorpay order for the registration fee (REG_PAY token required).")
    @PostMapping(value = "/initiate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InitiatePaymentResponse> initiate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InitiateRegistrationPaymentRequest req) {
        log.info("POST /payments/registration/initiate paymentId={} method={}",
                req.paymentId(), req.paymentMethod());
        // Verifier throws InvalidRegistrationTokenException on any
        // failure — GlobalExceptionHandler turns it into a 401.
        tokenVerifier.verifyForPayment(authorization, req.paymentId());
        return ResponseEntity.ok(registrationService.initiate(req));
    }

    @Operation(summary = "Verify Razorpay payment, mark PAID, activate the user (REG_PAY token required).")
    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationPaymentResultResponse> verify(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody VerifyRegistrationPaymentRequest req) {
        log.info("POST /payments/registration/verify paymentId={}", req.paymentId());
        String uid = tokenVerifier.verifyForPayment(authorization, req.paymentId());
        Long authUserId = Long.valueOf(uid);
        return ResponseEntity.ok(registrationService.verify(req, authUserId));
    }
}
