package com.spa.home_rental_application.property_service.property_service.DTO.Response;

/**
 * Public-facing representation of a property image. Hides the JPA entity
 * so internal columns (or any future audit fields) never leak through the API.
 */
public record PropertyImageResponseDTO(
        String id,
        String propertyId,
        String imageUrl,
        String type
) {}
