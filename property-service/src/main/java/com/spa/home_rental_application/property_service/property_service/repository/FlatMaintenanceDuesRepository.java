package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.FlatMaintenanceDues;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Per-flat dues override lookups. The hot path is "for flat X and
 * month Y, what's the latest override row?" — solved by the
 * effective-from-month <= target month + pick the most recent.
 */
@Repository
public interface FlatMaintenanceDuesRepository extends JpaRepository<FlatMaintenanceDues, String> {

    List<FlatMaintenanceDues> findByBuildingId(String buildingId);

    List<FlatMaintenanceDues> findByFlatIdOrderByEffectiveFromMonthDesc(String flatId);

    /** Most-recent override whose effective_from_month is <= target.
     *  Returns empty when the flat has no override and should use
     *  the building default. */
    @Query("""
            SELECT d FROM FlatMaintenanceDues d
            WHERE d.flatId = :flatId
              AND d.effectiveFromMonth <= :targetMonth
            ORDER BY d.effectiveFromMonth DESC
           """)
    List<FlatMaintenanceDues> findEffectiveDues(
            @Param("flatId") String flatId,
            @Param("targetMonth") String targetMonth);

    default Optional<FlatMaintenanceDues> findEffectiveDuesFor(String flatId, String targetMonth) {
        List<FlatMaintenanceDues> hits = findEffectiveDues(flatId, targetMonth);
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }
}
