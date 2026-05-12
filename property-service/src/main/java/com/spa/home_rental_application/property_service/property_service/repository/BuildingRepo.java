package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BuildingRepo extends JpaRepository<Building, String> {

    /**
     * Audit M8: filter soft-deleted rows at the DB level instead of
     * loading every building the owner ever had and stream-filtering
     * in Java. Saves the wire-bytes + JPA materialization for rows
     * the caller will discard.
     */
    @Query("SELECT b FROM Building b " +
           "WHERE b.ownerId = :ownerId " +
           "AND (b.isDeleted = false OR b.isDeleted IS NULL)")
    List<Building> findByOwnerId(@org.springframework.data.repository.query.Param("ownerId") String ownerId);

    @Query("SELECT b FROM Building b WHERE b.isDeleted = false OR b.isDeleted IS NULL")
    Page<Building> getActiveBuildings(Pageable pageable);
}
