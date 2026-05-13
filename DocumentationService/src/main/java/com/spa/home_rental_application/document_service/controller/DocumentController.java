package com.spa.home_rental_application.document_service.controller;

import com.spa.home_rental_application.document_service.DTO.Response.DocumentResponseDto;
import com.spa.home_rental_application.document_service.DTO.Response.ExtractedDataDto;
import com.spa.home_rental_application.document_service.DTO.Response.PreSignedUrlDto;
import com.spa.home_rental_application.document_service.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Documents", description = "Secure document upload, OCR extraction, and pre-signed downloads")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a document (multipart). Publishes document.uploaded.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDto> upload(
            @RequestParam("userId") @NotBlank String userId,
            @RequestParam("documentType") @NotBlank String documentType,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("POST /documents/upload userId={} type={} originalName={}",
                userId, documentType, file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(userId, documentType, file));
    }

    @Operation(summary = "Get document metadata")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDto> getById(@PathVariable("id") String documentId) {
        return ResponseEntity.ok(documentService.getById(documentId));
    }

    @Operation(summary = "List a user's active documents")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<DocumentResponseDto>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(documentService.getByUserId(userId));
    }

    @Operation(summary = "Get a pre-signed download URL (15-minute TTL by default)")
    @GetMapping("/{id}/download")
    public ResponseEntity<PreSignedUrlDto> getDownloadUrl(@PathVariable("id") String documentId,
                                                          HttpServletRequest req) {
        return ResponseEntity.ok(documentService.buildDownloadUrl(documentId, req));
    }

    /**
     * Pre-signed URL target — verifies HMAC + TTL, then streams the blob. We
     * deliberately put this on a different path ({@code /blob}) than the
     * metadata endpoint so it can be cached / proxied separately.
     */
    @Operation(summary = "Stream the actual file (verifies pre-signed signature + expiry)")
    @GetMapping("/{id}/blob")
    public ResponseEntity<InputStreamResource> downloadBlob(@PathVariable("id") String documentId,
                                                            @RequestParam("expires") long expires,
                                                            @RequestParam("signature") String signature) throws IOException {
        DocumentService.DownloadStream s = documentService.download(documentId, expires, signature);
        HttpHeaders headers = new HttpHeaders();
        if (s.contentType() != null) {
            headers.setContentType(MediaType.parseMediaType(s.contentType()));
        }
        headers.setContentDispositionFormData("attachment", s.filename());
        if (s.size() > 0) {
            headers.setContentLength(s.size());
        }
        return new ResponseEntity<>(new InputStreamResource(s.input()), headers, HttpStatus.OK);
    }

    @Operation(summary = "Run OCR / Document AI on the file (publishes document.extracted)")
    @PostMapping("/{id}/extract")
    public ResponseEntity<DocumentResponseDto> extract(@PathVariable("id") String documentId) {
        log.info("POST /documents/{}/extract", documentId);
        return ResponseEntity.ok(documentService.extract(documentId));
    }

    @Operation(summary = "Get the extracted (OCR) data for a document")
    @GetMapping("/{id}/extracted-data")
    public ResponseEntity<ExtractedDataDto> getExtracted(@PathVariable("id") String documentId) {
        return ResponseEntity.ok(documentService.getExtracted(documentId));
    }

    @Operation(summary = "Mark a document verified (admin / KYC) — publishes document.verified")
    @PostMapping("/{id}/verify")
    public ResponseEntity<DocumentResponseDto> verify(@PathVariable("id") String documentId,
                                                      @RequestParam("verifiedBy") @NotBlank String verifiedBy,
                                                      @RequestParam(value = "fraudFlag", defaultValue = "false") boolean fraudFlag) {
        log.info("POST /documents/{}/verify by={} fraud={}", documentId, verifiedBy, fraudFlag);
        return ResponseEntity.ok(documentService.verify(documentId, verifiedBy, fraudFlag));
    }

    /**
     * Issue #9 — owner approves a tenant-uploaded document. Sets
     * {@code verificationStatus=APPROVED} on the document, stamps
     * {@code decidedBy} / {@code decidedAt}, fires
     * {@code document.approved} Kafka event so the tenant gets a
     * notification fanned out across their channels.
     */
    @Operation(summary = "Owner approves a tenant-uploaded document (Issue #9)")
    @PostMapping("/{id}/approve")
    public ResponseEntity<DocumentResponseDto> approve(@PathVariable("id") String documentId,
                                                       @RequestParam("ownerId") @NotBlank String ownerId) {
        log.info("POST /documents/{}/approve by ownerId={}", documentId, ownerId);
        return ResponseEntity.ok(documentService.approve(documentId, ownerId));
    }

    /**
     * Issue #9 — owner rejects a tenant-uploaded document with a
     * free-text reason. Reason is required (Bean Validation on the
     * body) and surfaced verbatim in the tenant's notification.
     */
    @Operation(summary = "Owner rejects a tenant-uploaded document with a reason (Issue #9)")
    @PostMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentResponseDto> reject(
            @PathVariable("id") String documentId,
            @RequestParam("ownerId") @NotBlank String ownerId,
            @RequestBody @jakarta.validation.Valid
                com.spa.home_rental_application.document_service.DTO.Request.RejectDocumentRequest body) {
        log.info("POST /documents/{}/reject by ownerId={} reason={}",
                documentId, ownerId, body.reason());
        return ResponseEntity.ok(documentService.reject(documentId, ownerId, body.reason()));
    }

    @Operation(summary = "Soft-delete a document")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable("id") String documentId) {
        log.info("DELETE /documents/{}", documentId);
        documentService.softDelete(documentId);
        return ResponseEntity.noContent().build();
    }
}
