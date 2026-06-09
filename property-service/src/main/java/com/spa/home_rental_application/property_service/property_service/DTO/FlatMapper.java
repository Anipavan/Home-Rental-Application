package com.spa.home_rental_application.property_service.property_service.DTO;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Flat to/from FlatResponseDTO.
 *
 * The DTO embeds a small slice of the parent Building (name + address + city).
 * Three overloads make that ergonomic:
 *   - toResponseDTO(Flat)            -- fetches the parent (1 extra query)
 *   - toResponseDTO(Flat, Building)  -- caller already has the building
 *   - toResponseList(List<Flat>)     -- batch mode, fetches each unique parent once
 */
@Component
public class FlatMapper {

    private final BuildingRepo buildingRepo;

    @Autowired
    public FlatMapper(BuildingRepo buildingRepo) {
        this.buildingRepo = buildingRepo;
    }

    public Flat toEntity(FlatRequestDTO dto) {
        if (dto == null) return null;
        // Empty-string furnishing is treated as null (the @Pattern on
        // the DTO allows empty for form-serialisation; we don't want
        // empty strings in the DB).
        String furnishing = (dto.furnishingStatus() == null
                || dto.furnishingStatus().isBlank())
                ? null
                : dto.furnishingStatus();
        return Flat.builder()
                .buildingId(dto.buildingId())
                .flatNumber(dto.flatNumber())
                .floor(dto.floor())
                .bedrooms(dto.bedrooms())
                .bathrooms(dto.bathrooms())
                .areaSqft(dto.areaSqft())
                .rentAmount(dto.rentAmount())
                .tenantId(dto.tenantId())
                .leaseStartDate(dto.leaseStartDate())
                .leaseEndDate(dto.leaseEndDate())
                .furnishingStatus(furnishing)
                .petFriendly(dto.petFriendly())
                .availableFrom(dto.availableFrom())
                .depositAmount(dto.depositAmount())
                .description(dto.description())
                // Default to TRUE when the client omits the field —
                // keeps legacy + minimally-filled forms maximally
                // inclusive. The @Builder.Default on the entity also
                // applies, but explicit here makes the contract clear.
                .acceptsBachelor(dto.acceptsBachelor() == null ? Boolean.TRUE : dto.acceptsBachelor())
                .acceptsFamily(dto.acceptsFamily() == null ? Boolean.TRUE : dto.acceptsFamily())
                // V11: explicit listed-for-rent toggle. Default flipped
                // to TRUE — when the client omits the field (legacy
                // flat-new form, minimal API caller), the flat is
                // listed publicly automatically. Owners who don't want
                // a flat surfaced switch the toggle OFF in the
                // EditFlatDialog after creation.
                .availableForRent(dto.availableForRent() == null ? Boolean.TRUE : dto.availableForRent())
                .build();
    }

    public FlatResponseDTO toResponseDTO(Flat flat) {
        if (flat == null) return null;
        Building parent = (flat.getBuildingId() == null)
                ? null
                : buildingRepo.findById(flat.getBuildingId()).orElse(null);
        return toResponseDTO(flat, parent);
    }

    public FlatResponseDTO toResponseDTO(Flat flat, Building parent) {
        if (flat == null) return null;
        return new FlatResponseDTO(
                flat.getId(),
                flat.getBuildingId(),
                parent != null ? parent.getBuildingName()    : null,
                parent != null ? parent.getBuildingAddress() : null,
                parent != null ? parent.getBuildingCity()    : null,
                flat.getFlatNumber(),
                flat.getFloor(),
                flat.getBedrooms(),
                flat.getBathrooms(),
                flat.getAreaSqft(),
                flat.getRentAmount(),
                flat.getIsOccupied(),
                flat.getTenantId(),
                flat.getFlatOwnerId(),
                flat.getLeaseStartDate(),
                flat.getLeaseEndDate(),
                flat.getFurnishingStatus(),
                flat.getPetFriendly(),
                flat.getAvailableFrom(),
                flat.getDepositAmount(),
                flat.getDescription(),
                flat.getScheduledVacateDate(),
                flat.getScheduledVacateComments(),
                flat.getCreatedAt(),
                flat.getUpdatedAt(),
                // Legacy rows pre-migration return null from
                // getAcceptsBachelor/Family even though the column
                // has a DB-level default — Hibernate doesn't re-read
                // the row post-insert. Coerce to TRUE here so the FE
                // filter doesn't accidentally exclude old listings.
                flat.getAcceptsBachelor() == null ? Boolean.TRUE : flat.getAcceptsBachelor(),
                flat.getAcceptsFamily() == null ? Boolean.TRUE : flat.getAcceptsFamily(),
                // V11: legacy rows pre-migration return null; coerce
                // to TRUE here to match the new "listed by default"
                // behaviour. V11's backfill catches existing FALSE
                // rows so this only matters for entities that the
                // service mutated post-V10 without flushing.
                flat.getAvailableForRent() == null ? Boolean.TRUE : flat.getAvailableForRent()
        );
    }

    /**
     * Batch map. Fetches each unique parent building exactly once and
     * reuses it. Avoids the N+1 query toResponseDTO(Flat) would trigger
     * if called inside a stream.
     */
    public List<FlatResponseDTO> toResponseList(List<Flat> flats) {
        if (flats == null || flats.isEmpty()) return List.of();
        Map<String, Building> cache = new HashMap<>();
        return flats.stream()
                .map(f -> {
                    Building b = (f.getBuildingId() == null)
                            ? null
                            : cache.computeIfAbsent(f.getBuildingId(),
                                bid -> buildingRepo.findById(bid).orElse(null));
                    return toResponseDTO(f, b);
                })
                .toList();
    }
}
