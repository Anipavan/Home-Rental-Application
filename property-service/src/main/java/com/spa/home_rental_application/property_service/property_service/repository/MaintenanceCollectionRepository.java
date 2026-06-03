package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceCollection;
import com.spa.home_rental_application.property_service.property_service.enums.CollectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceCollectionRepository extends JpaRepository<MaintenanceCollection, String> {

    List<MaintenanceCollection> findByBuildingIdAndForMonthOrderByFlatId(
            String buildingId, String forMonth);

    Optional<MaintenanceCollection> findByFlatIdAndForMonth(String flatId, String forMonth);

    List<MaintenanceCollection> findByFlatIdOrderByForMonthDesc(String flatId);

    /** Total collected (PAID amount_paid) for a building in a month.
     *  Drives the "Collected this month" KPI on the ledger header.
     *  Returns 0 when nothing collected. */
    @Query("""
            SELECT COALESCE(SUM(c.amountPaid), 0)
              FROM MaintenanceCollection c
             WHERE c.buildingId = :buildingId
               AND c.forMonth = :month
               AND c.status = 'PAID'
           """)
    BigDecimal sumCollectedForMonth(@Param("buildingId") String buildingId,
                                    @Param("month") String month);

    /** Lifetime collected — running balance source alongside the
     *  expense sum. */
    @Query("""
            SELECT COALESCE(SUM(c.amountPaid), 0)
              FROM MaintenanceCollection c
             WHERE c.buildingId = :buildingId
               AND c.status = 'PAID'
           """)
    BigDecimal sumCollectedLifetime(@Param("buildingId") String buildingId);

    /**
     * Total collected across all 12 months of a single year — drives the
     * owner's "Collected this year" KPI tile. {@code yearPrefix} is the
     * 4-digit YYYY string ("2026"); the LIKE clause matches every
     * {@code forMonth} in that year (forMonth is YYYY-MM, so "2026-%"
     * cleanly partitions). Using a string prefix here keeps the query
     * independent of any database-specific date-extraction syntax —
     * Oracle EXTRACT vs Postgres date_part would otherwise diverge.
     */
    @Query("""
            SELECT COALESCE(SUM(c.amountPaid), 0)
              FROM MaintenanceCollection c
             WHERE c.buildingId = :buildingId
               AND c.forMonth LIKE :yearPrefix
               AND c.status = 'PAID'
           """)
    BigDecimal sumCollectedForYear(@Param("buildingId") String buildingId,
                                   @Param("yearPrefix") String yearPrefix);

    /** Outstanding (amount_due not yet paid) for the month — drives
     *  the "Outstanding" tile + the reminder list. */
    @Query("""
            SELECT COALESCE(SUM(c.amountDue), 0)
              FROM MaintenanceCollection c
             WHERE c.buildingId = :buildingId
               AND c.forMonth = :month
               AND c.status IN ('DUE', 'OVERDUE')
           """)
    BigDecimal sumOutstandingForMonth(@Param("buildingId") String buildingId,
                                      @Param("month") String month);

    long countByBuildingIdAndForMonthAndStatus(
            String buildingId, String forMonth, CollectionStatus status);
}
