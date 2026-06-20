package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_service.Dto.Request.ActivateRegistrationRequest;
import com.spa.home_rental_application.auth_service.Dto.Request.RegisterPendingRequest;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterPendingResponse;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Paid maintainer-registration endpoints. Sits apart from
 * {@link AuthController} so it's obvious which routes belong to the
 * monetised signup path. Two endpoints:
 *
 * <ul>
 *   <li>{@code POST /auth/register/pending} — public; the entry the
 *       frontend hits when the user picks the "I'm a maintainer" card.</li>
 *   <li>{@code POST /auth/internal/registration/activate/{authUserId}}
 *       — gateway-internal; payment-service Feigns into this on
 *       Razorpay PAID to flip the row to enabled.</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Auth — paid registration",
        description = "Paid maintainer-signup flow (pending registration + payment-driven activation)")
public class RegistrationPaymentController {

    private final AuthService authService;

    public RegistrationPaymentController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Create a disabled auth row + PENDING Payment for the paid maintainer-signup flow")
    @PostMapping(value = "/register/pending", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterPendingResponse> registerPending(
            @Valid @RequestBody RegisterPendingRequest req) {
        log.info("POST /auth/register/pending userName={}", req.userName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerPending(req));
    }

    /**
     * Internal: payment-service hits this from its Razorpay /verify
     * handler. The route sits under {@code /auth/internal/**}, so
     * Spring permits it but {@link com.spa.home_rental_application.auth_commons.GatewayAuthFilter}
     * blocks any direct hit lacking a valid {@code X-Internal-Auth-Sig}.
     */
    @Operation(summary = "Flip a paywall-disabled row to enabled (internal, called by payment-service on PAID)")
    @PostMapping(value = "/internal/registration/activate/{authUserId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthUserResponse> activateRegistration(
            @PathVariable Long authUserId,
            @Valid @RequestBody ActivateRegistrationRequest req) {
        log.info("POST /auth/internal/registration/activate/{} paymentId={}",
                authUserId, req.paymentId());
        return ResponseEntity.ok(authService.activateRegistration(authUserId, req.paymentId()));
    }
}
