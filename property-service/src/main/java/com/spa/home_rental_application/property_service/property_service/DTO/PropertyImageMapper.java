package com.spa.home_rental_application.property_service.property_service.DTO;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.PropertyImageResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;

public final class PropertyImageMapper {

    private PropertyImageMapper() {}

    public static PropertyImageResponseDTO toDTO(PropertyImage image) {
        if (image == null) return null;
        return new PropertyImageResponseDTO(
                image.getId(),
                image.getPropertyId(),
                image.getImageUrl(),
                image.getType()
        );
    }
}
