package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_service.Dto.Request.ResendVerificationRequest;
import com.spa.home_rental_application.auth_service.Dto.Request.VerifyEmailRequest;
import com.spa.home_rental_application.auth_service.Dto.Response.VerifyEmailResponse;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoints for the email-verification magic-link flow. Both
 * land at the gateway as {@code /rentals/v1/auth/verify-email} and
 * {@code /rentals/v1/auth/resend-verification}. No auth required —
 * the token in the request body IS the credential for verify; resend
 * is rate-limited inside the service.
 */
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Email Verification",
        description = "Magic-link gating for signup (toggleable via /admin/settings).")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Operation(summary = "Consume a verification token. Marks the user's email as verified.")
    @PostMapping(value = "/verify-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        UserDetails user = verificationService.verify(req.token());
        return ResponseEntity.ok(new VerifyEmailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified())));
    }

    @Operation(summary = "Email a fresh verification link. Idempotent; rate-limited to 3/hour.")
    @PostMapping(value = "/resend-verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest req) {
        verificationService.resend(req.email());
        // Always 200 with a generic body — don't leak which emails are
        // registered. The frontend renders the same "if it's registered,
        // we sent a link" message regardless.
        return ResponseEntity.ok(Map.of(
                "message", "If an unverified account exists for this email, a fresh verification link is on its way."));
    }
}
