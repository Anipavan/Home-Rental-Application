package com.spa.home_rental_application.document_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistent metadata for an uploaded document. The blob lives in object
 * storage (local dir or S3); only the {@code storage_url} is persisted here.
 * <p>
 * Soft-delete is preferred over physical delete to preserve audit trail.
 */
@Entity
@Table(name = "documents",
        indexes = {
                @Index(name = "ix_doc_user", columnList = "user_id"),
                @Index(name = "ix_doc_type", columnList = "document_type")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "document_type", length = 50, nullable = false)
    private String documentType;        // AADHAAR | PAN | AGREEMENT | PHOTO | OTHER

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "storage_backend", length = 20, nullable = false)
    @Builder.Default
    private String storageBackend = "LOCAL";

    @Column(name = "storage_url", length = 1000, nullable = false)
    private String storageUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "ocr_status", length = 20, nullable = false)
    @Builder.Default
    private String ocrStatus = "PENDING";   // PENDING | PROCESSING | DONE | FAILED

    @Lob
    @Column(name = "extracted_data")
    private String extractedDataJson;       // serialized JSON of extracted fields

    @Column(name = "fraud_flag", nullable = false)
    @Builder.Default
    private Boolean fraudFlag = false;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "verified_by", length = 50)
    private String verifiedBy;              // SYSTEM | ADMIN | KYC_PROVIDER

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.uploadedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
