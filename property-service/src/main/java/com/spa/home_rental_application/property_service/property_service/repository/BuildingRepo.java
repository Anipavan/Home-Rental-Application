package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BuildingRepo extends JpaRepository<Building, String> {
    List<Building> findByOwnerId(String ownerId);
    @Query("SELECT b FROM Building b WHERE b.isDeleted = false")
    Page<Building> getActiveBuildings(Pageable pageable);
}
