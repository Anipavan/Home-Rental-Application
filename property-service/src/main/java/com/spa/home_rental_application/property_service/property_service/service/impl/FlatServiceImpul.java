package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatOccupiedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents.FlatVacatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.FlatMapper;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatPreviewResponseDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.FlatSocietyMembership;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.FlatOccupiedException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.InvalidLeasePeriodException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.OutstandingDuesException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.client.PaymentClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatSocietyMembershipRepository;
import com.spa.home_rental_application.property_service.property_service.security.CallerSecurity;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FlatServiceImpul implements FlatService {

    private final FlatRepo flatRepo;
    private final BuildingRepo buildingRepo;
    private final PropertyServiceEvents eventProducer;
    private final FlatMapper flatMapper;
    private final AgreementService agreementService;
    private final UserClient userClient;
    private final PaymentClient paymentClient;
    private final FlatSocietyMembershipRepository membershipRepo;

    public FlatServiceImpul(FlatRepo flatRepo,
                            BuildingRepo buildingRepo,
                            PropertyServiceEvents eventProducer,
                            FlatMapper flatMapper,
                            AgreementService agreementService,
                            UserClient userClient,
                            PaymentClient paymentClient,
                            FlatSocietyMembershipRepository membershipRepo) {
        this.flatRepo = flatRepo;
        this.buildingRepo = buildingRepo;
        this.eventProducer = eventProducer;
        this.flatMapper = flatMapper;
        this.agreementService = agreementService;
        this.userClient = userClient;
        this.paymentClient = paymentClient;
        this.membershipRepo = membershipRepo;
    }

    @Override
    public Page<FlatResponseDTO> getAllFlats(Pageable pageable) {
        Page<Flat> flats = flatRepo.getActiveFlats(pageable);
        List<FlatResponseDTO> mapped = flatMapper.toResponseList(flats.getContent());
        return new PageImpl<>(mapped, pageable, flats.getTotalElements());
    }

    @Override
    public FlatResponseDTO getflatById(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));
        return flatMapper.toResponseDTO(flat);
    }

    @Override
    @Transactional
    public FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO) {
        Flat flat = flatMapper.toEntity(flatRequestDTO);
        if (flat.getId() == null || flat.getId().isBlank()) {
            flat.setId("FLT-" + UUID.randomUUID());
        }

        Building parent = (flat.getBuildingId() == null) ? null
                : buildingRepo.findById(flat.getBuildingId()).orElseThrow(
                    () -> new RecordNotFoundException(
                            "Building not found for buildingId=" + flat.getBuildingId()));

        // Ownership guard: an OWNER can only create flats inside their
        // own buildings. Resolved against the parent's persisted
        // ownerId so the request body can't lie.
        if (parent != null) {
            CallerSecurity.requireOwnerOrAdmin(parent.getOwnerId());
        }

        // V10: default flat_owner_id to the building owner when the
        // request doesn't specify one. The FLAT_OWNER membership-
        // claim flow later transfers ownership to a different user
        // (someone who bought the flat); until then, the building
        // owner is the de-facto flat owner for rent routing, lease
        // PDFs, etc. The V8 backfill caught EXISTING flats; this
        // catches NEW flats created post-V8.
        if (parent != null
                && (flat.getFlatOwnerId() == null || flat.getFlatOwnerId().isBlank())) {
            flat.setFlatOwnerId(parent.getOwnerId());
        }

        LocalDateTime now = LocalDateTime.now();
        if (flat.getCreatedAt() == null) flat.setCreatedAt(now);
        flat.setUpdatedAt(now);
        Flat saved = flatRepo.save(flat);

        syncBuildingFlatCount(parent);
        return flatMapper.toResponseDTO(saved, parent);
    }

    @Override
    @Transactional
    public FlatResponseDTO deleteFlatById(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        // Ownership guard: only the owner of the flat's parent
        // building (or admin) can soft-delete it.
        Building parentBuilding = (flat.getBuildingId() == null) ? null
                : buildingRepo.findById(flat.getBuildingId()).orElse(null);
        if (parentBuilding != null) {
            CallerSecurity.requireOwnerOrAdmin(parentBuilding.getOwnerId());
        }

        flat.setIsDeleted(true);
        flat.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(flat);

        syncBuildingFlatCount(parentBuilding);

        return flatMapper.toResponseDTO(saved, parentBuilding);
    }

    @Override
    public List<FlatResponseDTO> getflatsByBuildingId(String buildId) {
        buildingRepo.findById(buildId).orElseThrow(
                () -> new RecordNotFoundException("Building not found with id: " + buildId));
        return flatMapper.toResponseList(flatRepo.findByBuildingId(buildId));
    }

    @Override
    public List<FlatResponseDTO> getAllVacentFlats() {
        return flatMapper.toResponseList(flatRepo.findVacant());
    }

    @Override
    public List<FlatResponseDTO> getflatsByTenantId(String tenantId) {
        return flatMapper.toResponseList(flatRepo.findActiveByTenantId(tenantId));
    }

    /**
     * Haversine-distance "near me" geosearch.
     *
     * <p>We hydrate every vacant flat, look up its parent building's
     * coordinates, and compute the great-circle distance in
     * application code. For the current catalog scale (~hundreds of
     * listings) this is fine and avoids dragging in PostGIS or
     * Oracle Spatial. When the catalog grows past ~10k, swap to a
     * native spatial index (Oracle SDO_GEOMETRY or a Postgres
     * migration with the {@code earthdistance} extension).
     *
     * <p>Excludes occupied flats and buildings without geo-pins.
     */
    @Override
    public List<FlatResponseDTO> findFlatsNear(double lat, double lng, double radiusKm) {
        List<Flat> vacant = flatRepo.findVacant();
        if (vacant.isEmpty()) return List.of();

        // Cache parent-building lookups so we don't N+1 the DB if
        // many flats share a building.
        java.util.Map<String, Building> buildingCache = new java.util.HashMap<>();
        java.util.List<Flat> matches = new java.util.ArrayList<>();
        for (Flat f : vacant) {
            String bid = f.getBuildingId();
            if (bid == null) continue;
            Building b = buildingCache.computeIfAbsent(bid,
                    id -> buildingRepo.findById(id).orElse(null));
            if (b == null || b.getLatitude() == null || b.getLongitude() == null) {
                continue;
            }
            double d = haversineKm(lat, lng, b.getLatitude(), b.getLongitude());
            if (d <= radiusKm) matches.add(f);
        }
        return flatMapper.toResponseList(matches);
    }

    @Override
    @Transactional(readOnly = true)
    public FlatPreviewResponseDTO previewFlat(String buildingId, String flatNumber) {
        // Defensive null/blank handling — the endpoint is public and
        // unauthenticated, so anyone can ping it with anything. Treat
        // missing inputs as "not found" rather than throwing, so the
        // signup form can show a clean inline error without dealing
        // with HTTP 400s for empty form fields.
        if (buildingId == null || buildingId.isBlank()
                || flatNumber == null || flatNumber.isBlank()) {
            return FlatPreviewResponseDTO.notFound();
        }
        // findByBuildingIdAndFlatNumber already excludes soft-deleted
        // rows. Returns 0 or 1 matches in practice (the pair is logically
        // unique inside a building though we don't enforce it at the
        // DB layer). We pick the first match deterministically — same
        // as MembershipClaimServiceImpl.applyResidentApproval does.
        List<Flat> matches = flatRepo.findByBuildingIdAndFlatNumber(
                buildingId.trim(), flatNumber.trim());
        if (matches.isEmpty()) {
            return FlatPreviewResponseDTO.notFound();
        }
        Flat flat = matches.get(0);
        // Post-V15 definition of "occupied" for preview purposes:
        // either a rental tenant is assigned (flat.tenantId) OR at
        // least one active society member exists in
        // flat_society_membership. Purely-maintainee residents don't
        // touch tenantId anymore, so the old tenantId-only check would
        // report a flat as vacant even when a family (via society
        // membership) actually lives there — which would then wrongly
        // block a legit second-resident RESIDENT claim on the same
        // flat.
        boolean hasRentalTenant = flat.getTenantId() != null
                && !flat.getTenantId().isBlank();
        boolean hasSocietyMember = !membershipRepo
                .findByFlatIdAndIsActiveTrue(flat.getId()).isEmpty();
        return FlatPreviewResponseDTO.of(hasRentalTenant || hasSocietyMember);
    }

    /** Great-circle distance in km between two lat/lng pairs. */
    private static double haversineKm(double lat1, double lon1,
                                       double lat2, double lon2) {
        final double R = 6371.0088;          // Earth mean radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    @Transactional
    public FlatResponseDTO makeFlatVacate(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        // Ownership guard: only the building's owner (or an admin) can
        // vacate a flat. No-ops when there's no request context — same
        // call from Kafka listeners / scheduled jobs / tests is allowed.
        Building parentBuilding = buildingRepo.findById(flat.getBuildingId()).orElse(null);
        if (parentBuilding != null) {
            CallerSecurity.requireOwnerOrAdmin(parentBuilding.getOwnerId());
        }

        if (Boolean.FALSE.equals(flat.getIsOccupied())) {
            return flatMapper.toResponseDTO(flat);
        }

        // Issue #6: owner can vacate any time, no minimum-occupancy
        // restriction. The 2-month check previously here was lifted
        // because the spec says: "in the owner page, when owner clicks
        // on vacate option, there should not be any restrictions. he
        // can vacate anytime." Tenants still get the 60-day notice
        // window via scheduleVacate() — that's the right place to
        // enforce a delay, not this endpoint.

        return doVacate(flat, "owner-initiated");
    }

    /**
     * Shared vacate path used by both {@link #makeFlatVacate} (owner,
     * instant) and {@link #executeScheduledVacate} (scheduler, on
     * effective date). Clears tenant + occupancy, clears any
     * scheduledVacateDate stamp, fires flat.vacated event.
     */
    private FlatResponseDTO doVacate(Flat flat, String origin) {
        String flatId = flat.getId();
        String tenantBeingVacated = flat.getTenantId();
        String leaseEnd = flat.getLeaseEndDate() != null ? flat.getLeaseEndDate().toString() : null;

        flatRepo.markFlatVacant(flatId);
        flat.setIsOccupied(false);
        flat.setTenantId(null);
        flat.setScheduledVacateDate(null);
        flat.setVacateWarningSentAt(null);
        flat.setUpdatedAt(LocalDateTime.now());

        // V15: rental vacate ends the rental relationship, but the
        // person may still be a society member (owner-occupier who
        // was also renting a second flat, or a tenant who transitioned
        // to buying, etc.). We deactivate the specific mirrored row
        // that was created alongside the lease. Purely-maintainee
        // society members (created via a RESIDENT claim, not via
        // assign-flat) are untouched — they have their own lifecycle.
        if (tenantBeingVacated != null && !tenantBeingVacated.isBlank()) {
            membershipRepo.findByFlatIdAndUserId(flatId, tenantBeingVacated)
                    .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                    .ifPresent(m -> {
                        m.setIsActive(false);
                        membershipRepo.save(m);
                    });
        }

        log.info("Vacated flat {} (origin={} tenant={})", flatId, origin, tenantBeingVacated);

        // Best-effort, same reasoning as the assignFlat publish above.
        try {
            eventProducer.sendFlatVacated(FlatVacatedEvent.builder()
                    .eventType("flat.vacated")
                    .flatId(flatId)
                    .flatNumber(flat.getFlatNumber())
                    .tenantId(tenantBeingVacated)
                    .endDate(leaseEnd)
                    .timestamp(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("flat.vacated publish failed for flatId={} (proceeding): {}",
                    flatId, ex.getMessage());
        }

        return flatMapper.toResponseDTO(flat);
    }

    /* ─────────────────────────────────────────────────────────────────
     * Issue #5 — tenant-initiated scheduled vacate.
     *
     * Spec:
     *   - tenant clicks "Schedule vacate" → effectiveDate = today + 60d
     *   - rejected if any PENDING / OVERDUE rent invoice remains
     *   - flat stays occupied during the 60-day notice window
     *   - daily scheduler fires owner notification 10 days before
     *   - daily scheduler executes the actual vacate on the date itself
     * ────────────────────────────────────────────────────────────── */

    /** Spec: tenant-picked vacate date must be at least this far from today. */
    private static final int VACATE_NOTICE_PERIOD_DAYS = 60;

    @Override
    @Transactional
    public FlatResponseDTO scheduleVacate(String flatId, LocalDate effectiveDate, String comments) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        if (Boolean.FALSE.equals(flat.getIsOccupied()) || flat.getTenantId() == null) {
            throw new InvalidLeasePeriodException(
                    "Flat " + flat.getFlatNumber() + " is not occupied — nothing to vacate.");
        }

        // Caller must be the current tenant. Owner-initiated vacate
        // goes through makeFlatVacate which has its own ownership
        // gate; this path is tenant-only.
        CallerSecurity.requireSelfOrAdmin(flat.getTenantId());

        // Issue #4 — validate the tenant-picked date server-side
        // (defence in depth — the frontend's date picker enforces the
        // same floor but a direct API call could bypass it).
        if (effectiveDate == null) {
            throw new InvalidLeasePeriodException(
                    "Vacate date is required.");
        }
        LocalDate earliest = LocalDate.now().plusDays(VACATE_NOTICE_PERIOD_DAYS);
        if (effectiveDate.isBefore(earliest)) {
            // Mirror the FE wording so the toast surfaced from a direct
            // API call (or a client that skipped the FE check) reads
            // exactly like the inline FE error — easier for support to
            // grep across channels.
            throw new InvalidLeasePeriodException(
                    "Can't vacate the house before 60 days. "
                            + "Earliest move-out date: " + earliest + ".");
        }

        // Already scheduled? Idempotent — return existing date.
        if (flat.getScheduledVacateDate() != null) {
            log.info("Vacate already scheduled for flatId={} on {} — returning existing",
                    flatId, flat.getScheduledVacateDate());
            return flatMapper.toResponseDTO(flat);
        }

        // Dues check — the spec is "all dues should be cleared before
        // vacating". Backend defence-in-depth in addition to the
        // frontend's visible check, so a direct API call can't bypass.
        try {
            PaymentClient.UnpaidSummary unpaid = paymentClient.getUnpaidByFlat(flatId);
            if (unpaid != null && !unpaid.isClear()) {
                throw new OutstandingDuesException(String.format(
                        "Cannot schedule vacate — ₹%s in outstanding rent across %d invoice(s). Clear all dues first.",
                        unpaid.totalOutstanding(), unpaid.unpaidCount()));
            }
        } catch (OutstandingDuesException reraise) {
            throw reraise;
        } catch (Exception ex) {
            // PaymentClientFallback returns UnpaidSummary.unreachable
            // which is_clear()==false, so this branch is only hit if
            // Feign itself blows up (network/serialisation) before the
            // fallback. Fail closed.
            log.warn("Dues check failed for flatId={}: {} — failing closed", flatId, ex.getMessage());
            throw new OutstandingDuesException(
                    "Cannot verify outstanding dues right now. Try again in a minute.");
        }

        flat.setScheduledVacateDate(effectiveDate);
        flat.setVacateWarningSentAt(null);
        // Trim to the column max so a paste of a 5000-char essay
        // doesn't blow up the INSERT. Null comments are persisted as
        // null (existing rows pre-date this column).
        if (comments != null) {
            String trimmed = comments.trim();
            if (trimmed.isEmpty()) {
                flat.setScheduledVacateComments(null);
            } else if (trimmed.length() > 1000) {
                flat.setScheduledVacateComments(trimmed.substring(0, 1000));
            } else {
                flat.setScheduledVacateComments(trimmed);
            }
        } else {
            flat.setScheduledVacateComments(null);
        }
        flat.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(flat);
        log.info("Scheduled vacate for flatId={} tenantId={} effectiveDate={} commentsLen={}",
                flatId, flat.getTenantId(), effectiveDate,
                saved.getScheduledVacateComments() == null ? 0
                        : saved.getScheduledVacateComments().length());
        return flatMapper.toResponseDTO(saved);
    }

    @Override
    @Transactional
    public FlatResponseDTO cancelScheduledVacate(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        // Same authorisation as schedule — tenant who set it can cancel.
        if (flat.getTenantId() != null) {
            CallerSecurity.requireSelfOrAdmin(flat.getTenantId());
        }

        if (flat.getScheduledVacateDate() == null) {
            return flatMapper.toResponseDTO(flat);  // already not scheduled — idempotent
        }
        log.info("Cancelling scheduled vacate for flatId={} (was {})", flatId, flat.getScheduledVacateDate());
        flat.setScheduledVacateDate(null);
        flat.setVacateWarningSentAt(null);
        // Drop the old reason too — if they re-schedule they'll give a
        // fresh reason. Leaving the stale text would be misleading.
        flat.setScheduledVacateComments(null);
        flat.setUpdatedAt(LocalDateTime.now());
        return flatMapper.toResponseDTO(flatRepo.save(flat));
    }

    @Override
    @Transactional
    public FlatResponseDTO executeScheduledVacate(String flatId) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));
        // Idempotent — if already vacated (eg the scheduler re-ran),
        // just return the current state.
        if (Boolean.FALSE.equals(flat.getIsOccupied())) {
            return flatMapper.toResponseDTO(flat);
        }
        return doVacate(flat, "scheduler-executed");
    }

    @Override
    @Transactional
    public FlatResponseDTO updateFlat(String flatId, FlatRequestDTO flatRequestDTO) {
        Flat dto = flatMapper.toEntity(flatRequestDTO);
        Flat existing = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        // Ownership guard: only the owner of the flat's *current* parent
        // building (or admin) can mutate it. If the request also tries
        // to move the flat into a different building, that target
        // building must also belong to the caller — same caller, both
        // sides — otherwise an owner could relocate someone else's
        // flat under their own building.
        Building currentParent = buildingRepo.findById(existing.getBuildingId()).orElse(null);
        if (currentParent != null) {
            CallerSecurity.requireOwnerOrAdmin(currentParent.getOwnerId());
        }

        Building oldParent = null;
        if (dto.getBuildingId() != null && !dto.getBuildingId().equals(existing.getBuildingId())) {
            oldParent = buildingRepo.findById(existing.getBuildingId()).orElse(null);
            Building targetParent = buildingRepo.findById(dto.getBuildingId()).orElseThrow(
                    () -> new RecordNotFoundException(
                            "Building not found for buildingId=" + dto.getBuildingId()));
            CallerSecurity.requireOwnerOrAdmin(targetParent.getOwnerId());
        }

        existing.setBuildingId(dto.getBuildingId());
        existing.setFlatNumber(dto.getFlatNumber());
        existing.setFloor(dto.getFloor());
        existing.setBedrooms(dto.getBedrooms());
        existing.setBathrooms(dto.getBathrooms());
        existing.setAreaSqft(dto.getAreaSqft());
        existing.setRentAmount(dto.getRentAmount());
        existing.setLeaseStartDate(dto.getLeaseStartDate());
        existing.setLeaseEndDate(dto.getLeaseEndDate());
        // Listing-attribute updates: null on the request = "no change"
        // would be ergonomic, but we can't distinguish that from
        // "explicitly clear" without an Optional wrapper, so we go
        // with the simpler "always overwrite" semantics matching the
        // rest of this method.
        existing.setFurnishingStatus(dto.getFurnishingStatus());
        existing.setPetFriendly(dto.getPetFriendly());
        existing.setAvailableFrom(dto.getAvailableFrom());
        existing.setDepositAmount(dto.getDepositAmount());
        existing.setDescription(dto.getDescription());
        // Tenant-preference flags — same overwrite semantics. A null
        // here would clear the value; FlatMapper.toEntity already
        // defaults to TRUE for new creates, so the update path
        // preserves the same "default-open" contract by treating
        // null as TRUE rather than persisting a null.
        existing.setAcceptsBachelor(dto.getAcceptsBachelor() == null ? Boolean.TRUE : dto.getAcceptsBachelor());
        existing.setAcceptsFamily(dto.getAcceptsFamily() == null ? Boolean.TRUE : dto.getAcceptsFamily());
        // V10: listed-for-rent. Different default from the tenant-
        // preference flags above — we treat null as "leave the current
        // value" (rather than coerce to FALSE) so legacy clients
        // editing other fields don't accidentally un-list a flat.
        if (dto.getAvailableForRent() != null) {
            existing.setAvailableForRent(dto.getAvailableForRent());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(existing);

        if (oldParent != null) syncBuildingFlatCount(oldParent);
        Building newParent = (saved.getBuildingId() == null) ? null
                : buildingRepo.findById(saved.getBuildingId()).orElse(null);
        if (newParent != null) syncBuildingFlatCount(newParent);

        return flatMapper.toResponseDTO(saved, newParent);
    }

    @Override
    @Transactional
    public FlatResponseDTO assignFlat(String flatId, AssignFlatRequest req) {
        Flat flat = flatRepo.findById(flatId).orElseThrow(
                () -> new RecordNotFoundException("Flat not found with id: " + flatId));

        // Hide soft-deleted flats from the assignment surface — without
        // this a stale client (or an attacker holding a known deleted
        // flatId) could re-occupy a deleted listing. The new
        // findByBuildingId filters deleted rows from the listing
        // surface but POST /flats/{id}/assign loads the row by primary
        // key, so we need an explicit guard here.
        if (Boolean.TRUE.equals(flat.getIsDeleted())) {
            throw new RecordNotFoundException("Flat not found with id: " + flatId);
        }

        // Ownership guard: only the building's owner (or an admin) can
        // assign a tenant. Resolved before the occupancy check so a
        // poacher gets a clean 403 instead of leaking which flats are
        // already taken via the FlatOccupiedException.
        Building assigningBuilding = buildingRepo.findById(flat.getBuildingId()).orElse(null);
        if (assigningBuilding != null) {
            CallerSecurity.requireOwnerOrAdmin(assigningBuilding.getOwnerId());
        }

        // Refuse if the same tenant is already assigned to another active
        // (non-deleted) flat. Without this guard the same user could be
        // double-billed for two flats and the dashboard "My flat" page
        // would alternate which flat it surfaces.
        if (req.tenantId() != null && !req.tenantId().isBlank()) {
            // Audit H14: verify the tenantId resolves to a real
            // user-service profile. Without this an owner can pin a
            // flat to a phantom userId and the resulting lease /
            // payment chain will silently break later (Feign returns
            // an "empty" UserSummary via the fallback, and the deed
            // renders with blanks). 404 here gives the owner
            // immediate, actionable feedback.
            try {
                UserClient.UserSummary u = userClient.getUserById(req.tenantId());
                if (u == null || u.id() == null || u.id().isBlank()) {
                    throw new RecordNotFoundException(
                            "No user found with id " + req.tenantId() + " — pick an existing tenant.");
                }
            } catch (RecordNotFoundException ex) {
                throw ex;
            } catch (Exception ex) {
                // user-service unreachable. Don't fail-closed in dev
                // because the Hystrix fallback returns empty() and
                // tenant detail pages keep working. Log loudly and
                // proceed — the owner can retry once user-service is back.
                log.warn("user-service unreachable while validating tenant {} (proceeding): {}",
                        req.tenantId(), ex.getMessage());
            }

            List<Flat> tenantOther = flatRepo.findActiveByTenantId(req.tenantId());
            for (Flat other : tenantOther) {
                if (!other.getId().equals(flatId) && Boolean.TRUE.equals(other.getIsOccupied())) {
                    throw new FlatOccupiedException(
                            "Tenant " + req.tenantId() + " is already assigned to flat " + other.getId()
                                    + " — vacate it first before re-assigning.");
                }
            }
        }

        if (Boolean.TRUE.equals(flat.getIsOccupied())) {
            throw new FlatOccupiedException(
                    "Flat " + flatId + " is already occupied by tenant " + flat.getTenantId());
        }
        if (req.leaseEndDate().isBefore(req.leaseStartDate())) {
            throw new InvalidLeasePeriodException("leaseEndDate cannot be before leaseStartDate");
        }

        flat.setTenantId(req.tenantId());
        flat.setLeaseStartDate(req.leaseStartDate());
        flat.setLeaseEndDate(req.leaseEndDate());
        flat.setIsOccupied(true);
        flat.setUpdatedAt(LocalDateTime.now());
        Flat saved = flatRepo.save(flat);

        // V15: mirror the rental tenant into flat_society_membership
        // so the maintainer's per-flat resident list and the
        // maintenance-billing scan pick them up automatically. Owner
        // shouldn't have to double-enrol a tenant they've already
        // assigned. Idempotent — reactivates an existing row if the
        // same person moved out and back in.
        String approverId = CallerSecurity.getCurrentAuthUserId().orElse(null);
        membershipRepo.findByFlatIdAndUserId(saved.getId(), req.tenantId())
                .ifPresentOrElse(existing -> {
                    if (!Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        existing.setJoinedAt(LocalDateTime.now());
                        existing.setApprovedBy(approverId);
                        membershipRepo.save(existing);
                    }
                }, () -> membershipRepo.save(FlatSocietyMembership.builder()
                        .flatId(saved.getId())
                        .userId(req.tenantId())
                        .joinedAt(LocalDateTime.now())
                        .approvedBy(approverId)
                        .isActive(true)
                        .build()));

        // Best-effort. KafkaTemplate.send() returns a future, but the
        // very first call (or any call after a metadata refresh expires)
        // does a synchronous broker-metadata fetch that can take 10+
        // seconds when Kafka is slow / unreachable / Eureka hasn't
        // propagated the broker yet. Without this guard the gateway's
        // 10s response-timeout fires, the request 504s, and after 5
        // such failures the propertyServiceCircuitBreaker opens — at
        // which point even healthy assigns return "property service is
        // unavailable" via the fallback controller. Same pattern
        // already applied to DocumentationService.upload.
        try {
            eventProducer.sendFlatOccupied(FlatOccupiedEvent.builder()
                    .eventType("flat.occupied")
                    .flatId(saved.getId())
                    .flatNumber(saved.getFlatNumber())
                    .tenantId(saved.getTenantId())
                    .buildingId(saved.getBuildingId())
                    .rentAmount(saved.getRentAmount() != null ? saved.getRentAmount().doubleValue() : null)
                    .startDate(saved.getLeaseStartDate() != null ? saved.getLeaseStartDate().toString() : null)
                    .timestamp(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("flat.occupied publish failed for flatId={} (proceeding): {}",
                    saved.getId(), ex.getMessage());
        }

        // Auto-create a PENDING_SIGNATURE lease agreement so the tenant
        // immediately sees something to review and sign on their dashboard.
        // Failures here don't roll back the assignment -- owner can
        // regenerate via a manual endpoint later if it ever blows up.
        try {
            agreementService.createForAssignment(saved);
        } catch (Exception ex) {
            log.error("Could not auto-create lease agreement for flat {}", saved.getId(), ex);
        }

        return flatMapper.toResponseDTO(saved);
    }

    /**
     * Recompute the building's static buildingTotalFlats field to reflect
     * the live count of non-deleted flats. Keeps the legacy field truthful.
     */
    private void syncBuildingFlatCount(Building parent) {
        if (parent == null) return;
        long active = flatRepo.findByBuildingId(parent.getBuildingId()).stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .count();
        parent.setBuildingTotalFlats(String.valueOf(active));
        parent.setUpdatedDt(LocalDateTime.now().toString());
        buildingRepo.save(parent);
    }
}
