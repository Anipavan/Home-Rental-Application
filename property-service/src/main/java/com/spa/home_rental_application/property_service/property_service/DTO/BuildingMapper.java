package com.spa.home_rental_application.property_service.property_service.DTO;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Maps Building entity to/from Building DTOs.
 *
 * Two flavours of toDTO:
 *   - toDTO(Building)            -- live-count fields come back as 0 (legacy callers)
 *   - toDTO(Building, FlatRepo)  -- computes live active/occupied/vacant counts
 *
 * Number parsing is null/blank-safe so legacy rows with empty
 * buildingTotalFloors / buildingTotalFlats don't blow up.
 */
public class BuildingMapper {

    public static Building toEntity(BuildingRequestDTO dto) {
        if (dto == null) return null;
        return Building.builder()
                .buildingId(UUID.randomUUID().toString())
                .buildingName(dto.buildingName())
                .ownerId(dto.ownerId())
                .buildingAddress(dto.buildingAddress())
                .buildingCity(dto.buildingCity())
                .buildingState(dto.buildingState())
                .buildingTotalFloors(String.valueOf(dto.buildingTotalFloors()))
                .buildingTotalFlats(String.valueOf(dto.buildingTotalFlats()))
                .amenities(dto.amenities())
                .includedItems(dto.includedItems())
                // Optional reference IDs from the cascading dropdown. May be
                // null when the request comes from an old client / a script.
                .stateId(dto.stateId())
                .cityId(dto.cityId())
                // Optional geo pin — null is fine; geosearch excludes
                // pin-less buildings, map view falls back to centroid.
                .latitude(dto.latitude())
                .longitude(dto.longitude())
                .createdDt(LocalDateTime.now().toString())
                .updatedDt(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Live-count overload. Pass FlatRepo so we can return real counts for
     * active / occupied / vacant flats. Owner-verified status defaults to
     * {@code false} (cheaper path — skips the user-service round-trip).
     * Use the 3-arg overload to populate it.
     */
    public static BuildingResponseDTO toDTO(Building building, FlatRepo flatRepo) {
        return toDTO(building, flatRepo, null);
    }

    /**
     * Full-fat overload. Pass a {@link UserClient} so we can populate
     * {@code ownerVerified} by looking up the owner's KYC status. Use this
     * for the public detail endpoint where the badge matters; the count-
     * only overload is fine for owner-side listings (the owner already
     * knows whether they've verified themselves).
     *
     * <p>A failed Feign call falls through the {@code UserClientFallback}
     * (empty UserSummary → {@code isVerified()} returns false), so the
     * badge defaults to off when user-service is down — exactly what we
     * want for a trust signal.
     */
    public static BuildingResponseDTO toDTO(Building building, FlatRepo flatRepo, UserClient userClient) {
        if (building == null) return null;

        int active = 0;
        int occupied = 0;
        if (flatRepo != null && building.getBuildingId() != null) {
            List<Flat> flats = flatRepo.findByBuildingId(building.getBuildingId());
            for (Flat f : flats) {
                if (Boolean.TRUE.equals(f.getIsDeleted())) continue;
                active++;
                if (Boolean.TRUE.equals(f.getIsOccupied())) occupied++;
            }
        }

        boolean ownerVerified = false;
        if (userClient != null && building.getOwnerId() != null && !building.getOwnerId().isBlank()) {
            try {
                UserClient.UserSummary summary = userClient.getUserByAuthId(building.getOwnerId());
                if (summary != null) ownerVerified = summary.isVerified();
            } catch (Exception ignored) {
                // Defensive: a transient downstream blip should never
                // tank the response. ownerVerified just stays false.
            }
        }

        return new BuildingResponseDTO(
                building.getBuildingId(),
                building.getBuildingName(),
                building.getOwnerId(),
                building.getBuildingAddress(),
                building.getBuildingCity(),
                building.getBuildingState(),
                parseIntSafe(building.getBuildingTotalFloors()),
                parseIntSafe(building.getBuildingTotalFlats()),
                active,
                occupied,
                Math.max(0, active - occupied),
                building.getAmenities(),
                building.getLatitude(),
                building.getLongitude(),
                parseDateTimeSafe(building.getCreatedDt()),
                parseDateTimeSafe(building.getUpdatedDt()),
                building.getIncludedItems(),
                ownerVerified
        );
    }

    /** Convenience overload -- returns 0 for live-count fields. */
    public static BuildingResponseDTO toDTO(Building building) {
        return toDTO(building, null, null);
    }

    /* ----------------------------- helpers ----------------------------- */

    private static Integer parseIntSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDateTime parseDateTimeSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ex) {
            return null;
        }
    }
}
