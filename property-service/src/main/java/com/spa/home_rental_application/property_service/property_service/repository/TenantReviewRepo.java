package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.TenantReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantReviewRepo extends JpaRepository<TenantReview, String> {
    List<TenantReview> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<TenantReview> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    List<TenantReview> findByFlatIdOrderByCreatedAtDesc(String flatId);
}
