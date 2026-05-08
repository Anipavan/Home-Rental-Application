package com.spa.home_rental_application.property_service.property_service.DTO.Response;

public record CityResponseDTO(
        Long id,
        Long stateId,
        String stateName,
        String name,
        Short tier
) {}
