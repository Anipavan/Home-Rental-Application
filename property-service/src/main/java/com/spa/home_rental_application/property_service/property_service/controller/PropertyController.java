package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.PropertyImageResponseDTO;
import com.spa.home_rental_application.property_service.property_service.service.PropertyImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
}
