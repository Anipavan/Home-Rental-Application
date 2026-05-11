package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.PropertyImageMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.PropertyImageResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import com.spa.home_rental_application.property_service.property_service.repository.PropertyImageRepo;
import com.spa.home_rental_application.property_service.property_service.service.PropertyImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class PropertyImageServiceImpul implements PropertyImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final PropertyImageRepo repo;
    private final String uploadDir;

    public PropertyImageServiceImpul(PropertyImageRepo repo,
                                     @Value("${app.uploads.dir:uploads}") String uploadDir) {
        this.repo = repo;
        this.uploadDir = uploadDir;
    }

    @Override
    public PropertyImageResponseDTO uploadImage(String propertyId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 5 MB maximum");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        String safeOriginal = file.getOriginalFilename() == null
                ? "upload" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = UUID.randomUUID() + "_" + safeOriginal;
        Path target = dir.resolve(fileName);
        Files.write(target, file.getBytes());

        // First image uploaded for a property is auto-promoted to
        // cover so the listing card has a hero immediately without
        // requiring an extra "set cover" click. Subsequent uploads
        // stay non-cover until the owner explicitly promotes one.
        boolean firstImage = repo.findByPropertyId(propertyId).isEmpty();

        PropertyImage saved = repo.save(PropertyImage.builder()
                .propertyId(propertyId)
                .imageUrl(target.toString())
                .type(contentType)
                .isCover(firstImage)
                .sortOrder(firstImage ? 10 : 1000)
                .build());
        log.info("Stored image for property={} at {} (cover={})",
                propertyId, target, firstImage);
        return PropertyImageMapper.toDTO(saved);
    }

    @Override
    public List<PropertyImageResponseDTO> getImages(String propertyId) {
        // Cover first (so the gallery hero is deterministic), then
        // by sortOrder ascending for the rest. We sort in-memory
        // because the existing repo method doesn't take a Sort spec
        // and adding one would be a larger change to its callers.
        return repo.findByPropertyId(propertyId).stream()
                .sorted((a, b) -> {
                    boolean ac = Boolean.TRUE.equals(a.getIsCover());
                    boolean bc = Boolean.TRUE.equals(b.getIsCover());
                    if (ac != bc) return ac ? -1 : 1;
                    int as = a.getSortOrder() == null ? 1000 : a.getSortOrder();
                    int bs = b.getSortOrder() == null ? 1000 : b.getSortOrder();
                    return Integer.compare(as, bs);
                })
                .map(PropertyImageMapper::toDTO)
                .toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public PropertyImageResponseDTO setCover(String imageId) {
        PropertyImage target = repo.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("No image with id=" + imageId));
        if (Boolean.TRUE.equals(target.getIsCover())) {
            // Already cover — no-op.
            return PropertyImageMapper.toDTO(target);
        }
        // Unset the previous cover(s) on the same property, then
        // promote the new one. Doing this in a single transaction
        // means a viewer never sees a moment with zero covers.
        List<PropertyImage> siblings = repo.findByPropertyId(target.getPropertyId());
        for (PropertyImage s : siblings) {
            if (Boolean.TRUE.equals(s.getIsCover()) && !s.getId().equals(imageId)) {
                s.setIsCover(false);
                repo.save(s);
            }
        }
        target.setIsCover(true);
        // Cover also gets the lowest sortOrder so it stays first if
        // the owner later toggles cover off without reordering.
        target.setSortOrder(0);
        PropertyImage saved = repo.save(target);
        log.info("Set cover image {} for property {}", imageId, target.getPropertyId());
        return PropertyImageMapper.toDTO(saved);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<PropertyImageResponseDTO> reorder(String propertyId, List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return getImages(propertyId);
        List<PropertyImage> siblings = repo.findByPropertyId(propertyId);
        java.util.Map<String, PropertyImage> byId = new java.util.HashMap<>();
        for (PropertyImage s : siblings) byId.put(s.getId(), s);

        // Gaps of 10 leave room to slot a future image between two
        // existing ones without a full renumber.
        int step = 10;
        int nextOrder = step;
        for (String id : orderedIds) {
            PropertyImage img = byId.get(id);
            if (img == null) continue; // foreign id — silently skip
            img.setSortOrder(nextOrder);
            repo.save(img);
            nextOrder += step;
        }
        return getImages(propertyId);
    }

    /**
     * The DB row stores a local-filesystem path in {@code image_url}. Browsers
     * obviously can't load that as an &lt;img src&gt;, so the controller
     * fronts each image with /properties/images/{id}/raw and we read the file
     * bytes here.
     *
     * <p>Falls back to {@code application/octet-stream} when the persisted
     * type is missing or non-image (defensive — the upload path validates
     * content-type, but legacy rows may be incomplete).
     */
    /**
     * Hard-delete a stored property image. Removes the DB row first, then
     * tries to delete the file off disk best-effort — if the file is already
     * missing (manual cleanup, container restart, etc.) we don't blow up,
     * since the canonical state is the DB row.
     */
    @Override
    public void deleteImage(String imageId) throws IOException {
        PropertyImage img = repo.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("No image with id=" + imageId));
        repo.delete(img);
        if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
            try {
                Path file = Paths.get(img.getImageUrl());
                Files.deleteIfExists(file);
                log.info("Deleted property image id={} file={}", imageId, file);
            } catch (IOException e) {
                // The DB row is gone; the file leak is logged for ops.
                log.warn("Couldn't delete file for image {} (db row removed): {}",
                        imageId, e.getMessage());
            }
        }
    }

    @Override
    public RawImage readRaw(String imageId) throws IOException {
        PropertyImage img = repo.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("No image with id=" + imageId));
        String url = img.getImageUrl();
        if (url == null || url.isBlank()) {
            throw new IOException("Image " + imageId + " has no on-disk path");
        }
        Path file = Paths.get(url);
        if (!Files.exists(file)) {
            throw new IOException("Image file no longer exists on disk: " + file);
        }
        byte[] bytes = Files.readAllBytes(file);
        String ct = img.getType();
        if (ct == null || ct.isBlank() || !ct.startsWith("image/")) {
            ct = "application/octet-stream";
        }
        return new RawImage(bytes, ct);
    }
}
