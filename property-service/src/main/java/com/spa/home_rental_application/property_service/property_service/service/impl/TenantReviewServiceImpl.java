package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.CreateTenantReviewRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.TenantReviewResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.TenantReview;
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

    public TenantReviewServiceImpl(TenantReviewRepo repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public TenantReviewResponseDTO create(CreateTenantReviewRequest body) {
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
