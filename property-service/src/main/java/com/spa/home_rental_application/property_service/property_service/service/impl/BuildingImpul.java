package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.PropertyUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.BuildingMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.BuildingRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.BuildingResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.security.CallerSecurity;
import com.spa.home_rental_application.property_service.property_service.security.ForbiddenException;
import com.spa.home_rental_application.property_service.property_service.service.BuildingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BuildingImpul implements BuildingService {

    private final BuildingRepo building_repo;
    private final FlatRepo flat_repo;
    private final PropertyServiceEvents eventProducer;

    public BuildingImpul(BuildingRepo building_repo,
                         FlatRepo flat_repo,
                         PropertyServiceEvents eventProducer) {
        this.building_repo = building_repo;
        this.flat_repo = flat_repo;
        this.eventProducer = eventProducer;
    }

    @Override
    public Page<BuildingResponseDTO> getBuildings(Pageable pageable) {
        Page<Building> buildings = building_repo.getActiveBuildings(pageable);
        // Live counts -- pass the flat repo so each DTO carries real
        // active/occupied/vacant numbers, not the static "design capacity".
        return buildings.map(b -> BuildingMapper.toDTO(b, flat_repo));
    }

    @Override
    @Transactional
    public BuildingResponseDTO createBuilding(BuildingRequestDTO buildingRequestDTO) {
        log.info("Implimentation of building request.");

        Building building = BuildingMapper.toEntity(buildingRequestDTO);

        // Ownership guard: an OWNER can only create a building under
        // their own ownerId. Admins can create on behalf of anyone.
        // No-ops cleanly when there's no request context (Kafka, jobs,
        // tests).
        CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());

        if (building.getBuildingId() == null || building.getBuildingId().isBlank()) {
            String bid = String.valueOf(UUID.randomUUID());
            log.info("Building Id is found null, hence setting up the id to ID: {}", bid);
            building.setBuildingId("BLD-" + bid);
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(formatter);

        if (building.getCreatedDt() == null || building.getCreatedDt().isBlank()) {
            building.setCreatedDt(now);
        }
        building.setUpdatedDt(now);

        Building saved = building_repo.save(building);
        eventProducer.sendPropertyCreated(PropertyCreatedEvent.builder()
                .eventType("property.created")
                .propertyId(saved.getBuildingId())
                .ownerId(saved.getOwnerId())
                .timestamp(Instant.now())
                .build());
        return BuildingMapper.toDTO(saved, flat_repo);
    }

    @Override
    public BuildingResponseDTO getBuildingById(String buildId) {
        Building building = building_repo.findById(buildId).orElseThrow(
                () -> new RecordNotFoundException("No Record found with the given id :" + buildId));
        return BuildingMapper.toDTO(building, flat_repo);
    }

    @Override
    @Transactional
    public BuildingResponseDTO deleteBuildingById(String buildId) {
        Building building = building_repo.findById(buildId)
                .orElseThrow(() -> new RecordNotFoundException("No record found with id: " + buildId));

        // Ownership guard: only the building's owner (or admin) can
        // delete it. Resolved against the persisted ownerId — never
        // the request body — so a malicious caller can't lie about
        // who owns the building.
        CallerSecurity.requireOwnerOrAdmin(building.getOwnerId());

        // Real flat-existence check -- string field on the entity is unreliable
        // because it captures planned capacity, not the actual count of created flats.
        long activeFlats = flat_repo.findByBuildingId(buildId).stream()
                .filter(f -> Boolean.FALSE.equals(f.getIsDeleted()) || f.getIsDeleted() == null)
                .count();

        if (activeFlats > 0) {
            throw new BuildingHasFlatsException(
                    "Building " + buildId + " has " + activeFlats + " active flat(s) and cannot be deleted.");
        }

        building.setIsDeleted(true);
        building.setUpdatedDt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        building_repo.save(building);

        return BuildingMapper.toDTO(building, flat_repo);
    }

    @Override
    @Transactional
    public BuildingResponseDTO updateBuilding(String buildId, BuildingRequestDTO buildingRequestDTO) {
        Building building = BuildingMapper.toEntity(buildingRequestDTO);

        Building matchedBuilding = building_repo.findById(buildId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "No record found with the given id: " + buildId));

        // Ownership guard: only the building's current owner (or
        // admin) can mutate it. Read against the persisted ownerId —
        // never the request body.
        CallerSecurity.requireOwnerOrAdmin(matchedBuilding.getOwnerId());

        if (building.getBuildingName() != null && !building.getBuildingName().isBlank()) {
            matchedBuilding.setBuildingName(building.getBuildingName());
        }
        // ownerId transfer is restricted to admins. An owner trying to
        // hand-off ownership to someone else (or to themselves on a
        // different building) gets a 403 here — gives an explicit
        // signal rather than silently dropping the field.
        // Audit H16: also logs the transfer for compliance trail. The
        // request flow goes through the gateway with a JWT-stamped
        // X-Auth-User-Id, so the caller identity is captured.
        if (building.getOwnerId() != null && !building.getOwnerId().isBlank()
                && !building.getOwnerId().equals(matchedBuilding.getOwnerId())) {
            if (!CallerSecurity.isAdmin()) {
                log.warn("Refused building ownership transfer: caller={} buildingId={} from={} to={}",
                        CallerSecurity.getCurrentAuthUserId().orElse("?"),
                        buildId, matchedBuilding.getOwnerId(), building.getOwnerId());
                throw new ForbiddenException(
                        "Only an admin can transfer building ownership.");
            }
            log.info("ADMIN ownership transfer: buildingId={} from={} to={} actor={}",
                    buildId, matchedBuilding.getOwnerId(), building.getOwnerId(),
                    CallerSecurity.getCurrentAuthUserId().orElse("?"));
            matchedBuilding.setOwnerId(building.getOwnerId());
        }
        if (building.getBuildingAddress() != null && !building.getBuildingAddress().isBlank()) {
            matchedBuilding.setBuildingAddress(building.getBuildingAddress());
        }
        if (building.getBuildingCity() != null && !building.getBuildingCity().isBlank()) {
            matchedBuilding.setBuildingCity(building.getBuildingCity());
        }
        if (building.getBuildingState() != null && !building.getBuildingState().isBlank()) {
            matchedBuilding.setBuildingState(building.getBuildingState());
        }
        if (building.getBuildingTotalFloors() != null && !building.getBuildingTotalFloors().isBlank()) {
            matchedBuilding.setBuildingTotalFloors(building.getBuildingTotalFloors());
        }
        if (building.getBuildingTotalFlats() != null && !building.getBuildingTotalFlats().isBlank()) {
            matchedBuilding.setBuildingTotalFlats(building.getBuildingTotalFlats());
        }
        if (building.getAmenities() != null && !building.getAmenities().isBlank()) {
            matchedBuilding.setAmenities(building.getAmenities());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String now = LocalDateTime.now().format(formatter);
        matchedBuilding.setUpdatedDt(now);

        Building saved = building_repo.save(matchedBuilding);

        eventProducer.sendPropertyUpdated(PropertyUpdatedEvent.builder()
                .eventType("Property-Updated")
                .propertyId(saved.getBuildingId())
                .ownerId(saved.getOwnerId())
                .timestamp(Instant.now())
                .build());
        return BuildingMapper.toDTO(saved, flat_repo);
    }

    @Override
    public List<BuildingResponseDTO> getBuildingsByOwnerId(String ownerId) {
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        // Empty list is a valid state -- a brand new owner who hasn't listed
        // anything yet. The frontend dashboard renders an "empty" card here,
        // not a 404.
        return ownerBuildings.stream()
                .map(b -> BuildingMapper.toDTO(b, flat_repo))
                .collect(Collectors.toList());
    }

    /**
     * Case-insensitive client-side search on the active buildings list.
     * Owner-scoped if {@code ownerId} is non-null. Truncated to {@code limit}.
     *
     * <p>For our current scale (≤ a few hundred buildings per owner) the
     * naive in-memory filter is fine; if the catalog grows we'd swap this
     * for a JPA {@code @Query} or full-text index.
     */
    @Override
    public List<BuildingResponseDTO> searchBuildings(String q, String ownerId, int limit) {
        if (q == null || q.isBlank()) return List.of();
        // Audit L3: push the filter + cap down to the DB so we don't
        // materialize every active building only to discard most of
        // them. The needle is lower-cased + wrapped in % here so the
        // JPQL LIKE matches the same substring semantics the old
        // in-memory version had.
        String needle = "%" + q.toLowerCase().trim() + "%";
        int cap = Math.min(Math.max(limit, 1), 50);
        org.springframework.data.domain.Pageable page =
                org.springframework.data.domain.PageRequest.of(0, cap);

        List<Building> rows = (ownerId != null && !ownerId.isBlank())
                ? building_repo.searchActiveByOwner(ownerId, needle, page).getContent()
                : building_repo.searchActive(needle, page).getContent();

        return rows.stream()
                .map(b -> BuildingMapper.toDTO(b, flat_repo))
                .collect(Collectors.toList());
    }

    /**
     * Returns the tenantIds of every currently-occupied flat across every
     * building owned by the given owner. Returns an empty list (not 404)
     * when the owner has no occupied flats -- callers expect a list.
     */
    @Override
    public List<String> getTenantIdsByOwner(String ownerId) {
        List<Building> ownerBuildings = building_repo.findByOwnerId(ownerId);
        if (ownerBuildings.isEmpty()) {
            return List.of();
        }
        return ownerBuildings.stream()
                .flatMap(b -> flat_repo.findByBuildingId(b.getBuildingId()).stream())
                .filter(f -> Boolean.TRUE.equals(f.getIsOccupied()))
                .map(f -> f.getTenantId())
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
