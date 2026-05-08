package com.spa.home_rental_application.document_service.storage;

import com.spa.home_rental_application.document_service.Exceptionclass.StorageException;
import com.spa.home_rental_application.document_service.config.DocumentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-filesystem storage. Files land under
 * {@code app.documents.local-dir/{userId}/{documentId}_{originalFilename}}.
 * The active impl is selected by {@code app.documents.storage-backend}.
 */
@Component
@ConditionalOnProperty(prefix = "app.documents", name = "storage-backend",
        havingValue = "LOCAL", matchIfMissing = true)
@Slf4j
public class LocalDocumentStorage implements DocumentStorage {

    private final DocumentProperties props;

    public LocalDocumentStorage(DocumentProperties props) {
        this.props = props;
    }

    @Override
    public String backendName() {
        return "LOCAL";
    }

    @Override
    public String store(String documentId, String userId, MultipartFile file) throws IOException {
        Path userDir = Paths.get(props.getLocalDir(), userId);
        Files.createDirectories(userDir);
        String safeName = sanitize(file.getOriginalFilename());
        Path target = userDir.resolve(documentId + "_" + safeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Stored document {} ({} bytes) at {}", documentId, file.getSize(), target);
        return target.toAbsolutePath().toString();
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path path = Paths.get(storageKey);
        if (!Files.exists(path)) {
            throw new StorageException("Stored object not found: " + storageKey);
        }
        return Files.newInputStream(path);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path path = Paths.get(storageKey);
        if (Files.exists(path)) {
            // Soft-delete by renaming with a .deleted suffix — cron can purge later
            Path target = path.resolveSibling(path.getFileName() + ".deleted");
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
