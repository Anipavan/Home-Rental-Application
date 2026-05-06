package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepo extends JpaRepository<Owners, String> {

    Optional<Owners> findFirstByUserId(String userId);

    boolean existsByGstNumber(String gstNumber);
    boolean existsByPanNumber(String panNumber);
}
