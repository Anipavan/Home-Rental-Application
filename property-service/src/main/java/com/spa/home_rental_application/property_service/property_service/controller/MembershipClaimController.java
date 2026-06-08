package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.DecideMembershipClaimRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.MembershipClaimResponse;
import com.spa.home_rental_application.property_service.property_service.service.MembershipClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Membership-claim endpoints. Hangs off {@code /society/claims} so it
 * lives alongside the society dashboard endpoints in the API surface
 * — they're conceptually one workflow (an owner's society needs a
 * maintainer; this is how non-tenants apply).
 *
 * <p>All routes require auth. Per-action authorisation lives in the
 * service layer (owner-of-building check on approve/reject, self-check
 * on withdraw, logged-in check on create/list-mine).
 */
@RestController
@RequestMapping(value = "/society/claims", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Membership Claims",
        description = "Self-service maintainer / resident applications + owner approvals")
public class MembershipClaimController {

    private final MembershipClaimService service;

    public MembershipClaimController(MembershipClaimService service) {
        this.service = service;
    }

    @Operation(summary = "Submit a new membership claim (caller as maintainer or resident applicant).")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MembershipClaimResponse> create(
            @Valid @RequestBody CreateMembershipClaimRequest body) {
        log.info("POST /society/claims building={} role={} flatNumber={}",
                body.buildingId(), body.requestedRole(), body.claimedFlatNumber());
        return ResponseEntity.ok(service.createClaim(body));
    }

    @Operation(summary = "Owner: list every pending claim across my buildings.")
    @GetMapping("/pending/owner")
    public ResponseEntity<List<MembershipClaimResponse>> pendingForOwner() {
        return ResponseEntity.ok(service.listPendingForOwner());
    }

    @Operation(summary = "Current maintainer: list dual-approval claims awaiting my decision.")
    @GetMapping("/pending/maintainer")
    public ResponseEntity<List<MembershipClaimResponse>> pendingForMaintainer() {
        return ResponseEntity.ok(service.listPendingForCurrentMaintainer());
    }

    @Operation(summary = "Caller: list my own claims (any status).")
    @GetMapping("/mine")
    public ResponseEntity<List<MembershipClaimResponse>> mine() {
        return ResponseEntity.ok(service.listMine());
    }

    @Operation(summary = "Owner: approve a pending claim.")
    @PutMapping(value = "/{claimId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MembershipClaimResponse> approve(
            @PathVariable String claimId,
            @RequestBody(required = false) DecideMembershipClaimRequest body) {
        log.info("PUT /society/claims/{}/approve", claimId);
        return ResponseEntity.ok(service.approveClaim(claimId, body));
    }

    @Operation(summary = "Owner: reject a pending claim.")
    @PutMapping(value = "/{claimId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MembershipClaimResponse> reject(
            @PathVariable String claimId,
            @RequestBody(required = false) DecideMembershipClaimRequest body) {
        log.info("PUT /society/claims/{}/reject", claimId);
        return ResponseEntity.ok(service.rejectClaim(claimId, body));
    }

    @Operation(summary = "Caller: withdraw my own pending claim.")
    @PutMapping("/{claimId}/withdraw")
    public ResponseEntity<MembershipClaimResponse> withdraw(@PathVariable String claimId) {
        log.info("PUT /society/claims/{}/withdraw", claimId);
        return ResponseEntity.ok(service.withdrawClaim(claimId));
    }
}
