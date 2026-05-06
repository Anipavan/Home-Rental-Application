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

        PropertyImage saved = repo.save(PropertyImage.builder()
                .propertyId(propertyId)
                .imageUrl(target.toString())
                .type(contentType)
                .build());
        log.info("Stored image for property={} at {}", propertyId, target);
        return PropertyImageMapper.toDTO(saved);
    }

    @Override
    public List<PropertyImageResponseDTO> getImages(String propertyId) {
        return repo.findByPropertyId(propertyId).stream()
                .map(PropertyImageMapper::toDTO)
                .toList();
    }
}
