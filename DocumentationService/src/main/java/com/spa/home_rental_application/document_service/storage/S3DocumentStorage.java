package com.spa.home_rental_application.document_service.storage;

import com.spa.home_rental_application.document_service.Exceptionclass.StorageException;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/**
 * S3-backed object storage. Activated when
 * {@code app.documents.storage-backend=S3}.
 * <p>
 * Authentication uses the standard AWS SDK credential chain
 * ({@link DefaultCredentialsProvider}) — env vars, instance profile, or
 * profile config — so no secrets need to be baked into the image.
 * <p>
 * The {@code storageUrl} returned from {@link #store} and persisted on the
 * {@code documents} row is the **opaque S3 key** (e.g.
 * {@code prod/users/abc123/doc-456_passport.pdf}), NOT a public URL.
 */
@Component
@ConditionalOnProperty(prefix = "app.documents", name = "storage-backend", havingValue = "S3")
@Slf4j
public class S3DocumentStorage implements DocumentStorage {

    private final DocumentProperties props;
    private S3Client s3;

    public S3DocumentStorage(DocumentProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.getS3Bucket() == null || props.getS3Bucket().isBlank()) {
            throw new IllegalStateException(
                    "app.documents.s3-bucket must be set when storage-backend=S3");
        }

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.getS3Region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isS3PathStyleAccess())
                        .build());
        if (props.getS3EndpointOverride() != null && !props.getS3EndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getS3EndpointOverride()));
        }
        this.s3 = builder.build();
        log.info("S3 storage initialised bucket={} region={} endpointOverride={} pathStyle={}",
                props.getS3Bucket(), props.getS3Region(),
                props.getS3EndpointOverride(), props.isS3PathStyleAccess());
    }

    @PreDestroy
    void close() {
        if (s3 != null) s3.close();
    }

    @Override
    public String backendName() {
        return "S3";
    }

    @Override
    public String store(String documentId, String userId, MultipartFile file) throws IOException {
        String key = keyFor(documentId, userId, file.getOriginalFilename());
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(props.getS3Bucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                // Server-side encryption: AES-256 by default. Bucket policy
                // can also enforce KMS — leaving SDK default keeps options open.
                .serverSideEncryption("AES256")
                .build();
        try (InputStream in = file.getInputStream()) {
            s3.putObject(req, RequestBody.fromInputStream(in, file.getSize()));
        } catch (Exception ex) {
            throw new StorageException(
                    "S3 putObject failed for key=" + key + ": " + ex.getMessage(), ex);
        }
        log.info("Stored document {} ({} bytes) at s3://{}/{}",
                documentId, file.getSize(), props.getS3Bucket(), key);
        return key;
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(props.getS3Bucket())
                    .key(storageKey)
                    .build(), ResponseTransformer.toInputStream());
        } catch (NoSuchKeyException nf) {
            throw new StorageException("S3 object not found: " + storageKey);
        } catch (Exception ex) {
            throw new StorageException("S3 getObject failed for key=" + storageKey, ex);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getS3Bucket())
                    .key(storageKey)
                    .build());
            log.info("Deleted s3://{}/{}", props.getS3Bucket(), storageKey);
        } catch (Exception ex) {
            // Soft-delete is the database-level action; we don't fail the
            // service flow on S3 cleanup errors. Log and move on.
            log.warn("S3 deleteObject failed for key={}: {}", storageKey, ex.getMessage());
        }
    }

    /**
     * {prefix}users/{userId}/{documentId}_{sanitizedFilename}. The user-id
     * segment makes per-user lifecycle policies / quota easy to apply at
     * the bucket level.
     */
    private String keyFor(String documentId, String userId, String originalFilename) {
        String safe = sanitize(originalFilename);
        String prefix = props.getS3KeyPrefix() == null ? "" : props.getS3KeyPrefix();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) prefix = prefix + "/";
        // Treat null userId defensively so a buggy caller can't blow up the put.
        String safeUser = (userId == null || userId.isBlank()) ? "_unknown" : userId;
        return prefix + "users/" + safeUser + "/" + documentId + "_" + safe;
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file-" + UUID.randomUUID();
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
