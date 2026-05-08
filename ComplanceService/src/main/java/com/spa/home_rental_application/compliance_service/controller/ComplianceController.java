package com.spa.home_rental_application.compliance_service.controller;

import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateGstInvoiceRequest;
import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateReraLeaseRequest;
import com.spa.home_rental_application.compliance_service.DTO.Request.ReraRegisterRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.GstInvoiceResponseDto;
import com.spa.home_rental_application.compliance_service.DTO.Response.ReraRegistrationResponseDto;
import com.spa.home_rental_application.compliance_service.service.GstInvoiceService;
import com.spa.home_rental_application.compliance_service.service.ReraService;
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
import java.util.Map;

@RestController
@RequestMapping(value = "/compliance", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Compliance", description = "RERA registration + GST invoicing")
public class ComplianceController {

    private final ReraService reraService;
    private final GstInvoiceService gstInvoiceService;

    public ComplianceController(ReraService reraService, GstInvoiceService gstInvoiceService) {
        this.reraService = reraService;
        this.gstInvoiceService = gstInvoiceService;
    }

    // ---------- RERA ----------

    @Operation(summary = "Register a property with the state RERA portal")
    @PostMapping(value = "/rera/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReraRegistrationResponseDto> registerRera(
            @Valid @RequestBody ReraRegisterRequest request) {
        log.info("POST /compliance/rera/register propertyId={} state={}",
                request.propertyId(), request.state());
        return ResponseEntity.status(HttpStatus.CREATED).body(reraService.register(request));
    }

    @Operation(summary = "Get RERA registration status for a property (one row per state)")
    @GetMapping("/rera/status/{propertyId}")
    public ResponseEntity<List<ReraRegistrationResponseDto>> reraStatus(@PathVariable String propertyId) {
        return ResponseEntity.ok(reraService.getStatusForProperty(propertyId));
    }

    @Operation(summary = "Build RERA metadata to embed in a lease deed (called by Lease Service)")
    @PostMapping(value = "/lease/generate-rera/{leaseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> generateReraLease(
            @PathVariable String leaseId,
            @Valid @RequestBody GenerateReraLeaseRequest request) {
        log.info("POST /compliance/lease/generate-rera/{} state={}", leaseId, request.state());
        String metadata = reraService.generateReraLeaseMetadata(request);
        return ResponseEntity.ok(Map.of(
                "leaseId", leaseId,
                "reraMetadata", metadata));
    }

    // ---------- GST ----------

    @Operation(summary = "Generate a GST invoice for a settled payment (publishes gst.invoice.generated)")
    @PostMapping(value = "/gst/generate/{paymentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GstInvoiceResponseDto> generateGst(
            @PathVariable String paymentId,
            @Valid @RequestBody GenerateGstInvoiceRequest request) {
        log.info("POST /compliance/gst/generate/{} owner={}", paymentId, request.ownerId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gstInvoiceService.generate(paymentId, request));
    }

    @Operation(summary = "Get a GST invoice by id")
    @GetMapping("/gst/invoice/{id}")
    public ResponseEntity<GstInvoiceResponseDto> getInvoice(@PathVariable String id) {
        return ResponseEntity.ok(gstInvoiceService.getById(id));
    }

    @Operation(summary = "Download the GST invoice PDF")
    @GetMapping("/gst/invoice/{id}/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable String id) throws IOException {
        byte[] pdf = gstInvoiceService.getPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice-" + id + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
