package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.PropertyImageResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface PropertyImageService {
    PropertyImageResponseDTO uploadImage(String propertyId, MultipartFile file) throws IOException;
    List<PropertyImageResponseDTO> getImages(String propertyId);

    /** Holder for raw image bytes + content type so the controller can stream them. */
    record RawImage(byte[] bytes, String contentType) {}

    /**
     * Read bytes for a stored property image so the controller can stream them
     * back to the browser. Throws when the image row is missing or the on-disk
     * file has been removed.
     */
    RawImage readRaw(String imageId) throws IOException;
}
