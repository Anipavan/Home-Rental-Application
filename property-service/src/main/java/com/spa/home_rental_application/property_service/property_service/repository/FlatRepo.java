package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface FlatRepo extends JpaRepository<Flat,String> {

    /**
     * Active flats belonging to a building. Soft-deleted rows are
     * excluded — previous {@code findByBuildingId} silently returned
     * deleted flats and they would leak into the public listing and
     * (worse) re-appear in the assignment picker. The filter clause
     * is the canonical "(isDeleted = false OR isDeleted IS NULL)" so
     * legacy rows where the column is null still count as active.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.buildingId = :buildingId " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findByBuildingId(@Param("buildingId") String buildingId);

    /**
     * Vacant flats (across the entire catalog) — used by the public
     * "Browse vacant" endpoint. Soft-deleted rows are excluded; the
     * old derived query {@code findByIsOccupiedFalse} was returning
     * deleted flats and they were re-assignable through {@code POST
     * /flats/{id}/assign} because the assign call didn't re-check
     * isDeleted either.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.isOccupied = false " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findVacant();

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

    /**
     * All non-deleted flats this user owns. Driver query for the
     * "my flats" panel on the owner dashboard now that per-flat
     * ownership (V8) means an OWNER user might hold only specific
     * units in a building they didn't construct.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.flatOwnerId = :flatOwnerId " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findByFlatOwnerId(@Param("flatOwnerId") String flatOwnerId);

    /**
     * Look up a flat by (building, flat-number) — used by the
     * membership-claim approval path to bind a self-registered
     * resident to the flat they claimed. Returns the first match;
     * the (building_id, flat_number) pair is logically unique inside
     * a building but we don't enforce it at the DB level today, so
     * "first match" is what we can promise.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.buildingId = :buildingId " +
           "AND f.flatNumber = :flatNumber " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findByBuildingIdAndFlatNumber(
            @Param("buildingId") String buildingId,
            @Param("flatNumber") String flatNumber);
    @Modifying
    @Transactional
    @Query("UPDATE Flat f SET f.isOccupied = false, f.tenantId = null, " +
            "f.scheduledVacateDate = null, f.vacateWarningSentAt = null " +
            "WHERE f.id = :flatId")
    int markFlatVacant(@Param("flatId") String flatId);

    /**
     * Sweep A — owners who need their 10-day-before-vacate warning.
     * Returns active flats with a scheduled vacate landing in the
     * next {@code days} days where the warning hasn't been sent yet.
     * Used by {@code VacateScheduler}.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.scheduledVacateDate IS NOT NULL " +
           "AND f.scheduledVacateDate <= :cutoff " +
           "AND f.vacateWarningSentAt IS NULL " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findDueForVacateWarning(@Param("cutoff") LocalDate cutoff);

    /**
     * Sweep B — flats whose scheduled vacate date has arrived and
     * are still occupied. These get executed (isOccupied=false,
     * tenantId=null, flat.vacated event fires). Used by
     * {@code VacateScheduler}.
     */
    @Query("SELECT f FROM Flat f " +
           "WHERE f.scheduledVacateDate IS NOT NULL " +
           "AND f.scheduledVacateDate <= :today " +
           "AND f.isOccupied = true " +
           "AND (f.isDeleted = false OR f.isDeleted IS NULL)")
    List<Flat> findDueForVacateExecution(@Param("today") LocalDate today);
    @Query("SELECT f FROM Flat f WHERE f.isDeleted = false OR f.isDeleted IS NULL")
    Page<Flat> getActiveFlats(Pageable pageable);
}
