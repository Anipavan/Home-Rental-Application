package com.spa.home_rental_application.auth_service.Controller;

import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.MessageResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Service.AuthService;
import com.spa.home_rental_application.auth_service.enums.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Auth", description = "Authentication, registration, token lifecycle, password reset")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user (publishes user.registered)")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        log.info("POST /auth/register userName={} role={}", req.userName(), req.userRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @Operation(summary = "Log in. Returns access JWT + opaque refresh token")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest httpReq) {
        log.info("POST /auth/login userName={}", req.userName());
        AuthResponse resp = authService.login(req,
                clientIp(httpReq), httpReq.getHeader("User-Agent"));
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Rotate refresh token. Returns a new access JWT + new refresh token")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @Operation(summary = "Log out. Revokes the supplied refresh token")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req);
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @Operation(summary = "Begin a forgot-password flow. Always 200 — does not reveal whether the email exists")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.startPasswordReset(req);
        return ResponseEntity.ok(new MessageResponse(
                "If the email is registered, a reset link has been sent."));
    }

    @Operation(summary = "Complete a forgot-password flow with the emailed token")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.completePasswordReset(req);
        return ResponseEntity.ok(new MessageResponse("Password updated"));
    }

    @Operation(summary = "List users with the given role (ADMIN only)")
    @GetMapping("/role/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuthUserResponse>> getUserByRole(@PathVariable String roleName) {
        Roles role = parseRole(roleName);
        return ResponseEntity.ok(authService.getUsersByRole(role));
    }

    @Operation(summary = "Get an auth user by id (ADMIN only)")
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getById(id));
    }


    private static Roles parseRole(String input) {
        try {
            return Roles.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role: " + input);
        }
    }


    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
