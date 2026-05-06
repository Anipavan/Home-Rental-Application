package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgreementRepo extends JpaRepository<Agreement, String> {
    List<Agreement> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<Agreement> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    Optional<Agreement> findFirstByFlatIdAndStatusOrderByCreatedAtDesc(String flatId, Agreement.Status status);
    List<Agreement> findByFlatIdOrderByCreatedAtDesc(String flatId);
}
