package com.spa.home_rental_application.compliance_service.repository;

import com.spa.home_rental_application.compliance_service.Entities.ReraRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReraRegistrationRepository extends JpaRepository<ReraRegistration, String> {

    List<ReraRegistration> findByPropertyId(String propertyId);

    Optional<ReraRegistration> findByPropertyIdAndState(String propertyId, String state);

    boolean existsByPropertyIdAndState(String propertyId, String state);

    List<ReraRegistration> findByOwnerId(String ownerId);
}
