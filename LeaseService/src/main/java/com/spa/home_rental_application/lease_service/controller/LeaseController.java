package com.spa.home_rental_application.lease_service.controller;

import com.spa.home_rental_application.lease_service.DTO.Request.CreateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.RenewLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.SignLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.TerminateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseHistoryDto;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseResponseDto;
import com.spa.home_rental_application.lease_service.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/lease", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Lease", description = "Lease lifecycle (DRAFT → ACTIVE → RENEWED / TERMINATED / EXPIRED)")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @Operation(summary = "Create a new lease (DRAFT)")
    @PostMapping(value = "/leases", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeaseResponseDto> createLease(@Valid @RequestBody CreateLeaseRequest request) {
        log.info("POST /lease/leases tenant={} flat={}", request.tenantId(), request.flatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(leaseService.create(request));
    }

    @Operation(summary = "Get a lease by id")
    @GetMapping("/leases/{id}")
    public ResponseEntity<LeaseResponseDto> getLease(@PathVariable String id) {
        return ResponseEntity.ok(leaseService.getById(id));
    }

    @Operation(summary = "Get all leases for a tenant")
    @GetMapping("/leases/tenant/{tenantId}")
    public ResponseEntity<List<LeaseResponseDto>> getByTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(leaseService.getByTenantId(tenantId));
    }

    @Operation(summary = "Lease history for a flat")
    @GetMapping("/leases/flat/{flatId}")
    public ResponseEntity<List<LeaseResponseDto>> getByFlat(@PathVariable String flatId) {
        return ResponseEntity.ok(leaseService.getByFlatId(flatId));
    }

    @Operation(summary = "Renew a lease (publishes lease.renewed)")
    @PutMapping(value = "/leases/{id}/renew", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeaseResponseDto> renew(@PathVariable("id") String leaseId,
                                                  @Valid @RequestBody RenewLeaseRequest request) {
        log.info("PUT /lease/leases/{}/renew newEnd={}", leaseId, request.newEndDate());
        return ResponseEntity.ok(leaseService.renew(leaseId, request));
    }

    @Operation(summary = "Terminate a lease (publishes lease.terminated)")
    @PutMapping(value = "/leases/{id}/terminate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeaseResponseDto> terminate(@PathVariable("id") String leaseId,
                                                      @Valid @RequestBody TerminateLeaseRequest request) {
        log.info("PUT /lease/leases/{}/terminate reason={}", leaseId, request.terminationReason());
        return ResponseEntity.ok(leaseService.terminate(leaseId, request));
    }

    @Operation(summary = "Mark a lease as digitally signed (publishes lease.signed)")
    @PostMapping(value = "/leases/{id}/sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeaseResponseDto> sign(@PathVariable("id") String leaseId,
                                                 @Valid @RequestBody SignLeaseRequest request) {
        log.info("POST /lease/leases/{}/sign provider={}", leaseId, request.signatureProvider());
        return ResponseEntity.ok(leaseService.sign(leaseId, request));
    }

    @Operation(summary = "Download the rendered lease deed PDF")
    @GetMapping("/leases/{id}/document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable("id") String leaseId) throws IOException {
        byte[] pdf = leaseService.downloadDeed(leaseId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "lease-" + leaseId + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @Operation(summary = "List leases expiring in the next N days")
    @GetMapping("/expiring")
    public ResponseEntity<List<LeaseResponseDto>> expiring(
            @RequestParam(defaultValue = "60") @Min(1) int days) {
        return ResponseEntity.ok(leaseService.getLeasesExpiringWithin(days));
    }

    @Operation(summary = "Generate a RERA-stamped lease deed PDF (calls Compliance Service)")
    @PostMapping(value = "/leases/generate-rera/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LeaseResponseDto> generateRera(@PathVariable("id") String leaseId,
                                                         @RequestParam("state") String state) {
        log.info("POST /lease/leases/generate-rera/{} state={}", leaseId, state);
        return ResponseEntity.ok(leaseService.generateReraStampedLease(leaseId, state));
    }

    @Operation(summary = "Audit history for a lease")
    @GetMapping("/leases/{id}/history")
    public ResponseEntity<List<LeaseHistoryDto>> history(@PathVariable("id") String leaseId) {
        return ResponseEntity.ok(leaseService.getHistory(leaseId));
    }
}
