package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.time.LocalDateTime;

/**
 * Response payload for a Building.
 *
 * Three flat-count fields surface the building's true state:
 *   - buildingTotalFlats   : planned/design capacity entered at creation
 *   - activeFlatsCount     : live count of non-deleted flats persisted under it
 *   - occupiedFlatsCount   : live count of those that are currently occupied
 *   - vacantFlatsCount     : activeFlatsCount - occupiedFlatsCount
 *
 * <p>{@code latitude} / {@code longitude} are nullable. Legacy buildings
 * without a pin still load; the geosearch endpoint excludes them and
 * the future map view falls back to a city-centroid marker.
 */
public record BuildingResponseDTO(
        String buildingId,
        String buildingName,
        String ownerId,
        String buildingAddress,
        String buildingCity,
        String buildingState,
        Integer buildingTotalFloors,
        Integer buildingTotalFlats,
        Integer activeFlatsCount,
        Integer occupiedFlatsCount,
        Integer vacantFlatsCount,
        String amenities,
        Double latitude,
        Double longitude,
        LocalDateTime createdDt,
        LocalDateTime updatedDt
) {}
