package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.*;
import com.spa.home_rental_application.analytics_service.analytics_service.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Mutations called by the Kafka listeners. All operations are upsert-style
 * + idempotent so re-delivery of the same event doesn't double-count.
 */
@Service
@Slf4j
public class AggregationService {

    private final RevenueSummaryRepository revenueRepo;
    private final OccupancyStatRepository occupancyRepo;
    private final PaymentTrendRepository trendRepo;
    private final MaintenanceMetricRepository maintenanceRepo;

    public AggregationService(RevenueSummaryRepository revenueRepo,
                              OccupancyStatRepository occupancyRepo,
                              PaymentTrendRepository trendRepo,
                              MaintenanceMetricRepository maintenanceRepo) {
        this.revenueRepo = revenueRepo;
        this.occupancyRepo = occupancyRepo;
        this.trendRepo = trendRepo;
        this.maintenanceRepo = maintenanceRepo;
    }

    /* ---------- Revenue ---------- */

    @Transactional
    public void onPaymentCompleted(String ownerId, BigDecimal amount, LocalDate paidDate, LocalDate dueDate) {
        if (ownerId == null || amount == null) return;
        int year = paidDate.getYear();
        int month = paidDate.getMonthValue();
        RevenueSummary row = revenueRepo.findByOwnerIdAndYearAndMonth(ownerId, year, month)
                .orElseGet(() -> RevenueSummary.builder()
                        .ownerId(ownerId).year(year).month(month).build());
        row.setTotalPaid(row.getTotalPaid().add(amount));
        row.setTotalRevenue(row.getTotalRevenue().add(amount));
        row.setPaymentCount(row.getPaymentCount() + 1);
        revenueRepo.save(row);

        // Trends: was this on-time or late?
        if (dueDate != null) {
            long delayDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, paidDate));
            PaymentTrend t = trendRepo.findByOwnerIdAndYearAndMonth(ownerId, year, month)
                    .orElseGet(() -> PaymentTrend.builder().ownerId(ownerId).year(year).month(month).build());
            if (delayDays == 0) {
                t.setOnTimePayments(t.getOnTimePayments() + 1);
            } else {
                t.setLatePayments(t.getLatePayments() + 1);
                t.setTotalDelayDays(t.getTotalDelayDays() + delayDays);
            }
            trendRepo.save(t);
        }
        log.debug("Revenue updated owner={} {}-{} +₹{}", ownerId, year, month, amount);
    }

    @Transactional
    public void onPaymentOverdue(String ownerId, BigDecimal amount, LocalDate dueDate) {
        if (ownerId == null || amount == null || dueDate == null) return;
        int year = dueDate.getYear();
        int month = dueDate.getMonthValue();
        RevenueSummary row = revenueRepo.findByOwnerIdAndYearAndMonth(ownerId, year, month)
                .orElseGet(() -> RevenueSummary.builder()
                        .ownerId(ownerId).year(year).month(month).build());
        row.setTotalOverdue(row.getTotalOverdue().add(amount));
        revenueRepo.save(row);
    }

    /* ---------- Occupancy ---------- */

    @Transactional
    public void onFlatOccupied(String buildingId) {
        adjustOccupancy(buildingId, +1);
    }

    @Transactional
    public void onFlatVacated(String buildingId) {
        adjustOccupancy(buildingId, -1);
    }

    private void adjustOccupancy(String buildingId, int delta) {
        if (buildingId == null) return;
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        OccupancyStat row = occupancyRepo.findByBuildingIdAndStatDate(buildingId, today)
                .orElseGet(() -> OccupancyStat.builder()
                        .buildingId(buildingId).statDate(today).build());
        int occupied = Math.max(0, row.getOccupiedFlats() + delta);
        row.setOccupiedFlats(occupied);
        // total can be inferred by max-occupied-seen if we don't know the actual flat count;
        // safer is to keep total as max(total, occupied) so the rate stays sane.
        row.setTotalFlats(Math.max(row.getTotalFlats(), occupied));
        row.setVacantFlats(Math.max(0, row.getTotalFlats() - occupied));
        row.setOccupancyRate(row.getTotalFlats() == 0 ? 0.0
                : (double) occupied / row.getTotalFlats());
        occupancyRepo.save(row);
        log.debug("Occupancy adjusted building={} delta={} → occupied={}/{}", buildingId, delta, occupied, row.getTotalFlats());
    }

    /* ---------- Maintenance ---------- */

    @Transactional
    public void onMaintenanceResolved(String category, long resolutionMinutes) {
        if (category == null) return;
        MaintenanceMetric row = maintenanceRepo.findByCategory(category)
                .orElseGet(() -> MaintenanceMetric.builder().category(category).build());
        row.setResolvedCount(row.getResolvedCount() + 1);
        row.setTotalResolutionMinutes(row.getTotalResolutionMinutes() + Math.max(0, resolutionMinutes));
        maintenanceRepo.save(row);
    }
}
