package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Expense ledger lookups. The hot path is "show me every expense
 * row for building X in month YYYY-MM, newest first" — backed by
 * the {@code idx_expense_building_month} composite index.
 */
@Repository
public interface MaintenanceExpenseRepository extends JpaRepository<MaintenanceExpense, String> {

    List<MaintenanceExpense> findByBuildingIdAndExpenseMonthOrderByPaidOnDateDesc(
            String buildingId, String expenseMonth);

    List<MaintenanceExpense> findByBuildingIdOrderByPaidOnDateDesc(String buildingId);

    /** Aggregate the total spent on a building in a month — drives
     *  the "Expenses this month: ₹X" KPI on the ledger header.
     *  Returns 0 when no expenses recorded (COALESCE'd in service). */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
              FROM MaintenanceExpense e
             WHERE e.buildingId = :buildingId
               AND e.expenseMonth = :month
           """)
    BigDecimal sumForMonth(@Param("buildingId") String buildingId,
                           @Param("month") String month);

    /** Lifetime total — drives the "Total spent" stat on the
     *  ledger and is the source for the running balance computation. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
              FROM MaintenanceExpense e
             WHERE e.buildingId = :buildingId
           """)
    BigDecimal sumLifetime(@Param("buildingId") String buildingId);
}
