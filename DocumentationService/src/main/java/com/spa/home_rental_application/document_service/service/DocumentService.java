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

    void softDelete(String documentId);

    /**
     * Lightweight holder so the controller can stream without leaking the
     * underlying storage details.
     */
    record DownloadStream(InputStream input, String contentType, String filename, long size) {}
}
