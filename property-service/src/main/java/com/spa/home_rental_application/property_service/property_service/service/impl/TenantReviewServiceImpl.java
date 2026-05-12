package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateTenantReviewRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.TenantReviewResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.Entities.TenantReview;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.repository.TenantReviewRepo;
import com.spa.home_rental_application.property_service.property_service.service.TenantReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TenantReviewServiceImpl implements TenantReviewService {

    private final TenantReviewRepo repo;
    private final FlatRepo flatRepo;
    private final BuildingRepo buildingRepo;

    public TenantReviewServiceImpl(TenantReviewRepo repo,
                                   FlatRepo flatRepo,
                                   BuildingRepo buildingRepo) {
        this.repo = repo;
        this.flatRepo = flatRepo;
        this.buildingRepo = buildingRepo;
    }

    @Override
    @Transactional
    public TenantReviewResponseDTO create(CreateTenantReviewRequest body) {
        // Audit L2: require proof that the named tenant actually
        // occupied (or is occupying) the named flat under the named
        // owner. Without this, anyone could POST a 1-star review for
        // any tenant on any flat — a real reputational-attack surface.
        //
        // We accept either CURRENT occupancy (the flat row still has
        // the tenant assigned) or HISTORICAL via the lease end-date
        // being non-null and within a sensible window. For now CURRENT
        // is enough; historical comes when the lease-history table
        // lands. The owner check confirms the reviewer matches the
        // building's owner so an unrelated owner can't slander
        // tenants on someone else's property.
        Flat flat = flatRepo.findById(body.flatId()).orElseThrow(
                () -> new RecordNotFoundException("Flat not found: " + body.flatId()));
        if (Boolean.TRUE.equals(flat.getIsDeleted())) {
            throw new RecordNotFoundException("Flat not found: " + body.flatId());
        }
        boolean tenantMatch = body.tenantId() != null && body.tenantId().equals(flat.getTenantId());
        if (!tenantMatch) {
            throw new IllegalArgumentException(
                    "Tenant " + body.tenantId() + " is not currently the occupant of flat "
                            + body.flatId() + " — only the assigned tenant can be reviewed.");
        }
        Building building = buildingRepo.findById(flat.getBuildingId()).orElse(null);
        if (building == null || !body.ownerId().equals(building.getOwnerId())) {
            throw new IllegalArgumentException(
                    "Owner " + body.ownerId() + " does not own the building that flat "
                            + body.flatId() + " belongs to — refusing the review.");
        }

        LocalDateTime now = LocalDateTime.now();
        TenantReview r = TenantReview.builder()
                .id("REV-" + UUID.randomUUID())
                .ownerId(body.ownerId())
                .tenantId(body.tenantId())
                .flatId(body.flatId())
                .buildingId(body.buildingId())
                .rating(body.rating())
                .comment(body.comment())
                .createdAt(now)
                .updatedAt(now)
                .build();
        TenantReview saved = repo.save(r);
        log.info("Tenant review created: id={} owner={} tenant={} rating={}",
                saved.getId(), saved.getOwnerId(), saved.getTenantId(), saved.getRating());
        return toDto(saved);
    }

    @Override
    public List<TenantReviewResponseDTO> forTenant(String tenantId) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toDto).toList();
    }

    @Override
    public List<TenantReviewResponseDTO> forOwner(String ownerId) {
        return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toDto).toList();
    }

    @Override
    public List<TenantReviewResponseDTO> forFlat(String flatId) {
        return repo.findByFlatIdOrderByCreatedAtDesc(flatId).stream().map(this::toDto).toList();
    }

    private TenantReviewResponseDTO toDto(TenantReview r) {
        return new TenantReviewResponseDTO(
                r.getId(), r.getOwnerId(), r.getTenantId(), r.getFlatId(), r.getBuildingId(),
                r.getRating(), r.getComment(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
