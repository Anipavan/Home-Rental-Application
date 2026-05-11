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

    /**
     * Hard-delete a property image. Removes both the DB row and (best-effort)
     * the on-disk file so we don't leak storage. No-op when the file no longer
     * exists; the row is still removed.
     */
    void deleteImage(String imageId) throws IOException;

    /**
     * Mark this image as the cover for its property and unmark every
     * other image of the same property. Returns the updated DTO.
     * Idempotent — calling on the already-cover image is a no-op
     * apart from the response trip.
     */
    PropertyImageResponseDTO setCover(String imageId);

    /**
     * Persist a new ordering. {@code orderedIds} must contain ids
     * that all belong to {@code propertyId}; any id that doesn't is
     * skipped. New sortOrder = 10, 20, 30… so future inserts /
     * partial reorders have room to slot in without renumbering
     * everything.
     */
    List<PropertyImageResponseDTO> reorder(String propertyId, List<String> orderedIds);
}
