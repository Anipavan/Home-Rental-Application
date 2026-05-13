package com.spa.home_rental_application.document_service.service;

import com.spa.home_rental_application.document_service.DTO.Response.DocumentResponseDto;
import com.spa.home_rental_application.document_service.DTO.Response.ExtractedDataDto;
import com.spa.home_rental_application.document_service.DTO.Response.PreSignedUrlDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DocumentService {

    DocumentResponseDto upload(String userId, String documentType, MultipartFile file) throws IOException;

    DocumentResponseDto getById(String documentId);

    List<DocumentResponseDto> getByUserId(String userId);

    PreSignedUrlDto buildDownloadUrl(String documentId, HttpServletRequest req);

    /** Verifies signature, then streams the underlying object. */
    DownloadStream download(String documentId, long expires, String signature) throws IOException;

    DocumentResponseDto extract(String documentId);

    ExtractedDataDto getExtracted(String documentId);

    DocumentResponseDto verify(String documentId, String verifiedBy, boolean fraudFlag);

    /**
     * Owner approves a tenant-uploaded document (Issue #9). Sets
     * verificationStatus=APPROVED, stamps decidedBy / decidedAt,
     * fires {@code document.approved} Kafka event so the tenant
     * gets a notification via notification-service.
     */
    DocumentResponseDto approve(String documentId, String ownerId);

    /**
     * Owner rejects a tenant-uploaded document (Issue #9). Sets
     * verificationStatus=REJECTED, stores the rejection reason
     * (visible to the tenant on their documents tab + in the
     * notification body), fires {@code document.rejected} Kafka
     * event.
     */
    DocumentResponseDto reject(String documentId, String ownerId, String reason);

    void softDelete(String documentId);

    /**
     * Lightweight holder so the controller can stream without leaking the
     * underlying storage details.
     */
    record DownloadStream(InputStream input, String contentType, String filename, long size) {}
}
