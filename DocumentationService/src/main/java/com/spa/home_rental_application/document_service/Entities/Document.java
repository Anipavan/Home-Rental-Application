package com.spa.home_rental_application.document_service.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistent metadata for an uploaded document. The blob lives in object
 * storage (local dir or S3); only the {@code storage_url} is persisted here.
 * <p>
 * Soft-delete is preferred over physical delete to preserve audit trail.
 *
 * <p><b>Why this implements {@link Persistable}:</b> {@code DocumentServiceImpl.upload()}
 * generates the UUID itself <em>before</em> calling {@code repository.save()} —
 * the storage layer needs the id to build the on-disk filename, so we
 * can't defer it to {@code @GeneratedValue}. With a pre-assigned id,
 * Spring Data JPA's default {@code isNew()} check ({@code id == null})
 * returns {@code false}, so {@code save()} calls {@code em.merge()}
 * instead of {@code em.persist()}. Merge then can't find the not-yet-
 * inserted row and throws
 * {@code StaleObjectStateException: "unsaved-value mapping was incorrect"}.
 *
 * <p>Implementing {@link Persistable} lets us override that check via
 * a transient {@code isNewEntity} flag that flips to {@code false} on
 * the first persist or load — so new rows go through {@code persist()}
 * and subsequent updates go through {@code merge()}, both behaving
 * correctly.
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
public class Document implements Persistable<String> {

    @Id
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

    /**
     * Transient marker that drives {@link #isNew()}. Starts {@code true}
     * for builder-constructed instances (the upload path), flips to
     * {@code false} the first time the entity is persisted or loaded
     * from the database. Not a column, never serialized.
     */
    @Transient
    @Builder.Default
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private boolean isNewEntity = true;

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

    /**
     * After the entity is loaded from DB or persisted for the first
     * time, it's no longer "new" — subsequent saves should merge
     * rather than persist.
     */
    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNewEntity = false;
    }

    @Override
    public boolean isNew() {
        return isNewEntity;
    }
}
