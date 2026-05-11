package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface FlatRepo extends JpaRepository<Flat,String> {
    List<Flat> findByBuildingId(String buildingId);
    List<Flat> findByIsOccupiedFalse();

    /**
     * Vacant flats listed after a watermark — used by
     * {@code SavedSearchMatcherScheduler} so we only consider flats
     * the user hasn't already been notified about. Soft-deleted +
     * occupied flats are filtered out at the DB level.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE (f.isDeleted = false OR f.isDeleted IS NULL) " +
           "AND f.isOccupied = false " +
           "AND f.createdAt IS NOT NULL " +
           "AND f.createdAt > :since")
    List<Flat> findVacantCreatedAfter(@Param("since") LocalDateTime since);

    /** All non-deleted flats currently assigned to this tenant. */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.tenantId = :tenantId " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findActiveByTenantId(@Param("tenantId") String tenantId);
    @Modifying
    @Transactional
    @Query("UPDATE Flat f SET f.isOccupied = false, f.tenantId = null " +
            "WHERE f.id = :flatId")
    int markFlatVacant(@Param("flatId") String flatId);
    @Query("SELECT f FROM Flat f WHERE f.isDeleted = false OR f.isDeleted IS NULL")
    Page<Flat> getActiveFlats(Pageable pageable);
}
