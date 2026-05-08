package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.PropertyImageResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.PropertyImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Property image upload + retrieval. Returns DTOs only — never the JPA
 * entity — so internal columns can never leak through the API.
 */
@RestController
@RequestMapping(value = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Property Images", description = "Upload and list images for buildings and flats")
public class PropertyController {

    private final PropertyImageService propertyImageService;

    public PropertyController(PropertyImageService propertyImageService) {
        this.propertyImageService = propertyImageService;
    }

    @Operation(summary = "Upload an image for a building or flat")
    @PostMapping(
            value = "/buildings/{id}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyImageResponseDTO> uploadImage(@PathVariable String id,
                                                                @RequestParam("file") MultipartFile file) throws IOException {
        log.info("POST /properties/buildings/{}/images filename={} size={}",
                id, file.getOriginalFilename(), file.getSize());
        PropertyImageResponseDTO dto = propertyImageService.uploadImage(id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "List images for a building or flat")
    @GetMapping("/buildings/{id}/images")
    public ResponseEntity<List<PropertyImageResponseDTO>> getImages(@PathVariable String id) {
        return ResponseEntity.ok(propertyImageService.getImages(id));
    }

    /**
     * Stream the raw image bytes for a stored property image.
     *
     * <p>The image's on-disk path lives in {@code PropertyImage.imageUrl}, but
     * the browser can't load that — it isn't a URL. The frontend points its
     * &lt;img&gt; at {@code /properties/images/{imageId}/raw}, this endpoint
     * reads the file off disk, and streams the bytes back with the correct
     * Content-Type.
     */
    @Operation(summary = "Stream the raw bytes of a property image (for <img src> use)")
    @GetMapping("/images/{imageId}/raw")
    public ResponseEntity<byte[]> getImageRaw(@PathVariable String imageId) throws IOException {
        PropertyImageService.RawImage raw = propertyImageService.readRaw(imageId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(raw.contentType()));
        headers.setContentLength(raw.bytes().length);
        // Property photos are immutable once uploaded — give the browser a
        // long cache so list pages don't re-fetch every render.
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
        return new ResponseEntity<>(raw.bytes(), headers, HttpStatus.OK);
    }
}
