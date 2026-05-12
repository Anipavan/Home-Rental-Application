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

    /**
     * Audit L3: push the case-insensitive search down to the DB and
     * cap the result set there instead of loading every active
     * building into the JVM and filtering in Java. The owner-scoped
     * variant (next method) is for the owner UI.
     *
     * <p>Substring match on name / address / city / state — same
     * fields the in-memory filter used to match. The {@code :needle}
     * parameter must arrive already-lowercased + wrapped in {@code
     * %…%} by the caller so the DB index hint (if any) is still
     * usable.
     */
    @Query("SELECT b FROM Building b WHERE " +
           "(b.isDeleted = false OR b.isDeleted IS NULL) " +
           "AND (LOWER(b.buildingName) LIKE :needle " +
           "  OR LOWER(b.buildingAddress) LIKE :needle " +
           "  OR LOWER(b.buildingCity) LIKE :needle " +
           "  OR LOWER(b.buildingState) LIKE :needle)")
    Page<Building> searchActive(@org.springframework.data.repository.query.Param("needle") String needle,
                                Pageable pageable);

    /** Same as {@link #searchActive} but scoped to a single owner. */
    @Query("SELECT b FROM Building b WHERE " +
           "b.ownerId = :ownerId " +
           "AND (b.isDeleted = false OR b.isDeleted IS NULL) " +
           "AND (LOWER(b.buildingName) LIKE :needle " +
           "  OR LOWER(b.buildingAddress) LIKE :needle " +
           "  OR LOWER(b.buildingCity) LIKE :needle " +
           "  OR LOWER(b.buildingState) LIKE :needle)")
    Page<Building> searchActiveByOwner(@org.springframework.data.repository.query.Param("ownerId") String ownerId,
                                       @org.springframework.data.repository.query.Param("needle") String needle,
                                       Pageable pageable);
}
