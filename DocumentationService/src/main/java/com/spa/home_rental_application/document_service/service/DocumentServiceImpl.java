package com.spa.home_rental_application.document_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentUploadedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentVerifiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.DocumentServiceEvents;
import com.spa.home_rental_application.document_service.DTO.Response.DocumentResponseDto;
import com.spa.home_rental_application.document_service.DTO.Response.ExtractedDataDto;
import com.spa.home_rental_application.document_service.DTO.Response.PreSignedUrlDto;
import com.spa.home_rental_application.document_service.Entities.Document;
import com.spa.home_rental_application.document_service.Exceptionclass.DocumentNotFoundException;
import com.spa.home_rental_application.document_service.Exceptionclass.InvalidDocumentException;
import com.spa.home_rental_application.document_service.Exceptionclass.StorageException;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
import com.spa.home_rental_application.document_service.mapper.DocumentMapper;
import com.spa.home_rental_application.document_service.ocr.OcrEngine;
import com.spa.home_rental_application.document_service.repository.DocumentRepository;
import com.spa.home_rental_application.document_service.storage.DocumentStorage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("AADHAAR", "PAN", "AGREEMENT", "PHOTO", "OTHER");

    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final DocumentStorage storage;
    private final OcrEngine ocrEngine;
    private final PreSignedUrlSigner urlSigner;
    private final DocumentServiceEvents events;
    private final DocumentProperties props;

    public DocumentServiceImpl(DocumentRepository repository,
                               DocumentMapper mapper,
                               DocumentStorage storage,
                               OcrEngine ocrEngine,
                               PreSignedUrlSigner urlSigner,
                               DocumentServiceEvents events,
                               DocumentProperties props) {
        this.repository = repository;
        this.mapper = mapper;
        this.storage = storage;
        this.ocrEngine = ocrEngine;
        this.urlSigner = urlSigner;
        this.events = events;
        this.props = props;
    }

    // ---------- Public API ----------

    @Override
    @Transactional
    public DocumentResponseDto upload(String userId, String documentType, MultipartFile file) throws IOException {
        validateType(documentType);
        validateFile(file);

        String documentId = UUID.randomUUID().toString();
        log.info("Upload userId={} type={} size={} → docId={}",
                userId, documentType, file.getSize(), documentId);

        String storageKey = storage.store(documentId, userId, file);

        Document doc = Document.builder()
                .id(documentId)
                .userId(userId)
                .documentType(documentType.toUpperCase())
                .originalFilename(file.getOriginalFilename())
                .storageBackend(storage.backendName())
                .storageUrl(storageKey)
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .ocrStatus("PENDING")
                .build();
        Document saved = repository.save(doc);

        // Publish best-effort. KafkaTemplate.send() returns a future but the
        // initial broker-metadata fetch is synchronous and can throw
        // KafkaException when Kafka is unreachable / Eureka hasn't resolved
        // the broker yet. Without this guard the user sees a generic
        // "An unexpected error occurred" toast for what's actually a
        // background-event failure on a successful upload — the file is on
        // disk and the row is in the DB. Downstream consumers can be
        // re-driven via a manual replay if event delivery matters; from
        // the user's perspective the upload should always succeed.
        publishUploadedSafe(saved);
        return mapper.toResponse(saved);
    }

    private void publishUploadedSafe(Document saved) {
        try {
            events.sendDocumentUploaded(DocumentUploadedEvent.builder()
                    .eventType("document.uploaded")
                    .documentId(saved.getId())
                    .userId(saved.getUserId())
                    .documentType(saved.getDocumentType())
                    .contentType(saved.getContentType())
                    .fileSizeBytes(saved.getFileSizeBytes())
                    .storageUrl(saved.getStorageUrl())
                    .uploadedAt(saved.getUploadedAt())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("document.uploaded publish failed for documentId={} (proceeding anyway): {}",
                    saved.getId(), ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponseDto getById(String documentId) {
        return mapper.toResponse(mustFind(documentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getByUserId(String userId) {
        return repository.findActiveByUserId(userId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PreSignedUrlDto buildDownloadUrl(String documentId, HttpServletRequest req) {
        Document doc = mustFind(documentId);
        PreSignedUrlSigner.Signed signed = urlSigner.sign(doc.getId());
        // Use the externally-reachable base URL from config — NOT
        // req.getServerName(), which on the document-service side
        // resolves to the internal Eureka-registered hostname (e.g.
        // localhost:8091). A URL built from that wouldn't go through
        // the gateway, so the browser couldn't reach it and the
        // internal-auth filter would block direct hits. See
        // DocumentProperties.publicBaseUrl for the rationale + env
        // override pattern.
        String base = props.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = reconstructBase(req); // belt-and-braces local fallback
        }
        String url = UriComponentsBuilder.fromUriString(base)
                .path("/documents/{id}/blob")
                .queryParam("expires", signed.expiresEpochSec())
                .queryParam("signature", signed.signature())
                .buildAndExpand(doc.getId())
                .toUriString();
        return new PreSignedUrlDto(doc.getId(), url, signed.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadStream download(String documentId, long expires, String signature) throws IOException {
        urlSigner.verify(documentId, expires, signature);
        Document doc = mustFind(documentId);
        return new DownloadStream(
                storage.open(doc.getStorageUrl()),
                doc.getContentType(),
                doc.getOriginalFilename() != null ? doc.getOriginalFilename() : doc.getId(),
                doc.getFileSizeBytes() == null ? -1L : doc.getFileSizeBytes());
    }

    @Override
    @Transactional
    public DocumentResponseDto extract(String documentId) {
        Document doc = mustFind(documentId);
        doc.setOcrStatus("PROCESSING");
        repository.save(doc);

        try {
            OcrEngine.OcrResult result = ocrEngine.extract(doc);
            doc.setExtractedDataJson(mapper.serializeExtracted(result.fields()));
            doc.setFraudFlag(result.fraudFlag());
            doc.setConfidenceScore(result.confidenceScore());
            doc.setOcrStatus("DONE");
            Document saved = repository.save(doc);

            // Same best-effort pattern as upload — extraction success
            // shouldn't 500 the API just because Kafka is flaky.
            try {
                events.sendDocumentExtracted(DocumentExtractedEvent.builder()
                        .eventType("document.extracted")
                        .documentId(saved.getId())
                        .userId(saved.getUserId())
                        .documentType(saved.getDocumentType())
                        .extractedData(result.fields())
                        .fraudFlag(saved.getFraudFlag())
                        .confidenceScore(saved.getConfidenceScore())
                        .extractedAt(LocalDateTime.now())
                        .timestamp(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                log.warn("document.extracted publish failed for documentId={} (proceeding): {}",
                        saved.getId(), ex.getMessage());
            }
            return mapper.toResponse(saved);
        } catch (Exception ex) {
            log.error("OCR extraction failed for documentId={}", documentId, ex);
            doc.setOcrStatus("FAILED");
            repository.save(doc);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExtractedDataDto getExtracted(String documentId) {
        return mapper.toExtractedDto(mustFind(documentId));
    }

    @Override
    @Transactional
    public DocumentResponseDto verify(String documentId, String verifiedBy, boolean fraudFlag) {
        Document doc = mustFind(documentId);
        doc.setVerifiedBy(verifiedBy);
        doc.setVerifiedAt(LocalDateTime.now());
        doc.setFraudFlag(fraudFlag);
        Document saved = repository.save(doc);

        try {
            events.sendDocumentVerified(DocumentVerifiedEvent.builder()
                    .eventType("document.verified")
                    .documentId(saved.getId())
                    .userId(saved.getUserId())
                    .documentType(saved.getDocumentType())
                    .verifiedBy(verifiedBy)
                    .fraudFlag(fraudFlag)
                    .verifiedAt(saved.getVerifiedAt())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("document.verified publish failed for documentId={} (proceeding): {}",
                    saved.getId(), ex.getMessage());
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(String documentId) {
        Document doc = mustFind(documentId);
        doc.setIsDeleted(true);
        doc.setDeletedAt(LocalDateTime.now());
        repository.save(doc);
        try {
            storage.delete(doc.getStorageUrl());
        } catch (IOException ex) {
            // Don't fail the soft-delete just because the blob couldn't be moved —
            // a janitor cron can clean up later.
            log.warn("Soft-deleted documentId={} but storage cleanup failed: {}",
                    documentId, ex.getMessage());
        }
    }

    // ---------- Helpers ----------

    private Document mustFind(String documentId) {
        return repository.findActiveById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(
                        "No active document with id=" + documentId));
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type.toUpperCase())) {
            throw new InvalidDocumentException(
                    "documentType must be one of " + ALLOWED_TYPES + " (got " + type + ")",
                    "INVALID_DOCUMENT_TYPE");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType != null && !props.getAllowedContentTypes().contains(contentType.toLowerCase())) {
            throw new InvalidDocumentException(
                    "Content-Type " + contentType + " not allowed; expected one of "
                            + props.getAllowedContentTypes(),
                    "UNSUPPORTED_CONTENT_TYPE");
        }
    }

    private String reconstructBase(HttpServletRequest req) {
        StringBuilder b = new StringBuilder();
        b.append(req.getScheme()).append("://").append(req.getServerName());
        if ((req.getScheme().equals("http") && req.getServerPort() != 80)
                || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
            b.append(':').append(req.getServerPort());
        }
        if (req.getContextPath() != null) {
            b.append(req.getContextPath());
        }
        return b.toString();
    }

    /** Bridge so the controller can call without importing StorageException directly. */
    @SuppressWarnings("unused")
    private static StorageException toStorage(IOException ex) {
        return new StorageException(ex.getMessage(), ex);
    }
}
