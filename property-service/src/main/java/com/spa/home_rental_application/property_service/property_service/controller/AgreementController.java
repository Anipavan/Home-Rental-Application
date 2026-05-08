package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.RejectAgreementRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.SignAgreementRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.AgreementResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Lease agreement endpoints. Mounted under /properties so the gateway
 * routes /rentals/v1/properties/agreements/** here.
 */
@RestController
@RequestMapping(value = "/properties/agreements", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Agreements", description = "Lease agreements: create on assignment, sign, reject")
public class AgreementController {

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @Operation(summary = "Get agreement by id")
    @GetMapping("/{id}")
    public ResponseEntity<AgreementResponseDTO> get(@PathVariable String id) {
        return ResponseEntity.ok(agreementService.getById(id));
    }

    @Operation(summary = "List all agreements for a tenant -- powers the tenant 'My agreement' view")
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<AgreementResponseDTO>> forTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(agreementService.getForTenant(tenantId));
    }

    @Operation(summary = "List all agreements for an owner")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<AgreementResponseDTO>> forOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(agreementService.getForOwner(ownerId));
    }

    @Operation(summary = "List all agreements for a flat (history)")
    @GetMapping("/flat/{flatId}")
    public ResponseEntity<List<AgreementResponseDTO>> forFlat(@PathVariable String flatId) {
        return ResponseEntity.ok(agreementService.getForFlat(flatId));
    }

    @Operation(summary = "Sign an agreement -- accepts a base64-encoded PNG of the tenant's signature")
    @PostMapping(value = "/{id}/sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgreementResponseDTO> sign(@PathVariable String id,
                                                     @RequestBody @Valid SignAgreementRequest body) {
        log.info("POST /agreements/{}/sign signatureBytes={}", id,
                body.signatureData() != null ? body.signatureData().length() : 0);
        return ResponseEntity.ok(agreementService.sign(id, body.signatureData()));
    }

    @Operation(summary = "Reject an agreement with a reason")
    @PostMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgreementResponseDTO> reject(@PathVariable String id,
                                                       @RequestBody @Valid RejectAgreementRequest body) {
        log.info("POST /agreements/{}/reject reason={}", id, body.reason());
        return ResponseEntity.ok(agreementService.reject(id, body.reason()));
    }

    @Operation(summary = "Download the rendered lease deed PDF")
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String id) throws IOException {
        byte[] pdf = agreementService.loadDocument(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "lease-agreement-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
