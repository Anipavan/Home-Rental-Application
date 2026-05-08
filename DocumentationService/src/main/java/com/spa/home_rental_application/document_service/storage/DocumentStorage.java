package com.spa.home_rental_application.document_service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pluggable object-storage interface so the service can swap LOCAL → S3
 * without code changes. Implementations are selected by
 * {@code app.documents.storage-backend}.
 */
public interface DocumentStorage {

    /** Identifier persisted on the {@code documents} row (LOCAL | S3). */
    String backendName();

    /**
     * Persists the upload and returns the opaque storage key (never a public
     * URL). Callers persist this in {@code documents.storage_url}.
     */
    String store(String documentId, String userId, MultipartFile file) throws IOException;

    /** Streams the persisted object back. Caller is responsible for closing. */
    InputStream open(String storageKey) throws IOException;

    /** Soft-delete (rename / mark) — actual blob removal is async in prod. */
    void delete(String storageKey) throws IOException;
}
