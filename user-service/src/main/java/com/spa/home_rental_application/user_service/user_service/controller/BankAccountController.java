package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountResponseDto;
import com.spa.home_rental_application.user_service.user_service.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Bank-account CRUD for the signed-in user. Every endpoint is gated
 * to "self or admin" via the gateway-stamped {@code X-Auth-User-Id}
 * + {@code X-Auth-Roles} headers — bank details are sensitive enough
 * that a tenant must not be able to read another tenant's account
 * details by guessing their userId. The full account number is never
 * returned (see {@code BankAccountServiceImpul.mask}).
 */
@RestController
@RequestMapping(value = "/users/bank-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Bank Accounts",
        description = "Self-managed bank-account details for payouts / refunds")
public class BankAccountController {

    private final BankAccountService service;

    public BankAccountController(BankAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Get the bank account on file for a user (self or admin)")
    @GetMapping("/user/{userId}")
    public ResponseEntity<BankAccountResponseDto> getByUserId(
            @PathVariable String userId,
            HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        return service.getByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "No bank account on file for userId=" + userId));
    }

    @Operation(summary = "Upsert the bank account on file for a user (self or admin)")
    @PutMapping(value = "/user/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BankAccountResponseDto> upsert(
            @PathVariable String userId,
            @Valid @RequestBody BankAccountRequestDto body,
            HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        log.info("PUT /users/bank-accounts/user/{} bank='{}'", userId, body.bankName());
        return ResponseEntity.ok(service.save(userId, body));
    }

    @Operation(summary = "Remove the user's bank account (self or admin)")
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> delete(@PathVariable String userId, HttpServletRequest req) {
        requireSelfOrAdmin(userId, req);
        service.delete(userId);
        return ResponseEntity.noContent().build();
    }

    /* --------------------------- authz ---------------------------- */

    /**
     * Self-or-admin guard backed by the gateway-stamped headers. We
     * treat a MISSING {@code X-Auth-User-Id} as a system / Feign
     * call (no header is set when the request bypasses the gateway
     * — only internal Feign clients with the signing interceptor
     * get through, so they're trusted by definition).
     */
    private static void requireSelfOrAdmin(String targetUserId, HttpServletRequest req) {
        String roles = req.getHeader(GatewayAuthFilter.HDR_ROLES);
        if (roles != null) {
            for (String r : roles.split(",")) {
                String trimmed = r.trim();
                if (trimmed.equalsIgnoreCase("ADMIN")
                        || trimmed.equalsIgnoreCase("ROLE_ADMIN")) {
                    return;
                }
            }
        }
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) {
            // No gateway header — internal Feign call. Allow.
            return;
        }
        if (!caller.equals(targetUserId)) {
            log.warn("Forbidden bank-account access: caller={} target={}",
                    caller, targetUserId);
            throw new ResponseStatusException(FORBIDDEN,
                    "You can only manage your own bank account.");
        }
    }
}
