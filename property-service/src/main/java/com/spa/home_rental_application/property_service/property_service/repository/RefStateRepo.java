package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.RefState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefStateRepo extends JpaRepository<RefState, Long> {

    /** All states alphabetically — used by the dropdown. */
    List<RefState> findAllByOrderByNameAsc();

    Optional<RefState> findByCodeIgnoreCase(String code);
}
