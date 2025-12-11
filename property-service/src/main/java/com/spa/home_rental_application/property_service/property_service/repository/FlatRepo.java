package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FlatRepo extends JpaRepository<Flat,String> {
    List<Flat> findByBuildingId(String buildingId);
    List<Flat> findByIsOccupiedFalse();
    @Modifying
    @Transactional
    @Query("UPDATE Flat f SET f.isOccupied = false, f.tenantId = null " +
            "WHERE f.id = :flatId")
    int markFlatVacant(@Param("flatId") String flatId);
}
