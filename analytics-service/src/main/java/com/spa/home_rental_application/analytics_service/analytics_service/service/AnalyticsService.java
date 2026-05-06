package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.AnalyticsMapper;
import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.*;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.OccupancyStat;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.PaymentTrend;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.RevenueSummary;
import com.spa.home_rental_application.analytics_service.analytics_service.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Read-side facade. Pure functions over the aggregate tables. */
@Service
@Slf4j
public class AnalyticsService {

    private final RevenueSummaryRepository revenueRepo;
    private final OccupancyStatRepository occupancyRepo;
    private final PaymentTrendRepository trendRepo;
    private final MaintenanceMetricRepository maintenanceRepo;

    public AnalyticsService(RevenueSummaryRepository revenueRepo,
                            OccupancyStatRepository occupancyRepo,
                            PaymentTrendRepository trendRepo,
                            MaintenanceMetricRepository maintenanceRepo) {
        this.revenueRepo = revenueRepo;
        this.occupancyRepo = occupancyRepo;
        this.trendRepo = trendRepo;
        this.maintenanceRepo = maintenanceRepo;
    }

    /* ---------- Revenue ---------- */

    public List<RevenueResponse> ownerRevenue(String ownerId) {
        return revenueRepo.findByOwnerId(ownerId).stream().map(AnalyticsMapper::toResponse).toList();
    }

    public List<RevenueResponse> monthlyRevenue(int year) {
        return revenueRepo.findByYear(year).stream().map(AnalyticsMapper::toResponse).toList();
    }

    public RevenueResponse yearlyTotalForOwner(String ownerId, int year) {
        List<RevenueSummary> rows = revenueRepo.findByOwnerId(ownerId).stream()
                .filter(r -> r.getYear() == year).toList();
        BigDecimal totalPaid = rows.stream().map(RevenueSummary::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = rows.stream().map(RevenueSummary::getTotalPending)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOverdue = rows.stream().map(RevenueSummary::getTotalOverdue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = rows.stream().mapToLong(RevenueSummary::getPaymentCount).sum();
        return new RevenueResponse(ownerId, year, 0,
                totalPaid.add(totalPending), totalPaid, totalPending, totalOverdue, count, null);
    }

    public ComparisonResponse compareMonthOverMonth(String ownerId) {
        LocalDate now = LocalDate.now();
        LocalDate prev = now.minusMonths(1);
        BigDecimal current = revenueRepo.findByOwnerIdAndYearAndMonth(ownerId, now.getYear(), now.getMonthValue())
                .map(RevenueSummary::getTotalPaid).orElse(BigDecimal.ZERO);
        BigDecimal previous = revenueRepo.findByOwnerIdAndYearAndMonth(ownerId, prev.getYear(), prev.getMonthValue())
                .map(RevenueSummary::getTotalPaid).orElse(BigDecimal.ZERO);
        BigDecimal delta = current.subtract(previous);
        Double pct = previous.signum() == 0 ? null
                : delta.multiply(BigDecimal.valueOf(100))
                        .divide(previous, 4, RoundingMode.HALF_UP).doubleValue();
        return new ComparisonResponse("month-over-month", current, previous, delta, pct);
    }

    /* ---------- Occupancy ---------- */

    public List<OccupancyResponse> buildingOccupancyTrend(String buildingId) {
        return occupancyRepo.findByBuildingIdOrderByStatDateAsc(buildingId).stream()
                .map(AnalyticsMapper::toResponse).toList();
    }

    public OccupancyResponse overallOccupancyForToday() {
        List<OccupancyStat> today = occupancyRepo.findByStatDate(LocalDate.now());
        int totalFlats = today.stream().mapToInt(OccupancyStat::getTotalFlats).sum();
        int occupied = today.stream().mapToInt(OccupancyStat::getOccupiedFlats).sum();
        int vacant = Math.max(0, totalFlats - occupied);
        double rate = totalFlats == 0 ? 0.0 : (double) occupied / totalFlats;
        return new OccupancyResponse("ALL", LocalDate.now(), totalFlats, occupied, vacant, rate);
    }

    /* ---------- Payment trends ---------- */

    public List<PaymentTrendResponse> trendsForOwner(String ownerId) {
        return trendRepo.findByOwnerIdOrderByYearAscMonthAsc(ownerId).stream()
                .map(AnalyticsMapper::toResponse).toList();
    }

    public double overallCollectionRateForOwner(String ownerId) {
        List<PaymentTrend> rows = trendRepo.findByOwnerIdOrderByYearAscMonthAsc(ownerId);
        long onTime = rows.stream().mapToLong(PaymentTrend::getOnTimePayments).sum();
        long late = rows.stream().mapToLong(PaymentTrend::getLatePayments).sum();
        long total = onTime + late;
        return total == 0 ? 1.0 : (double) onTime / total;
    }

    /* ---------- Maintenance ---------- */

    public List<MaintenanceMetricResponse> maintenanceByCategory() {
        return maintenanceRepo.findAll().stream().map(AnalyticsMapper::toResponse).toList();
    }

    public double averageResolutionMinutes() {
        List<MaintenanceMetricResponse> rows = maintenanceByCategory();
        long count = rows.stream().mapToLong(MaintenanceMetricResponse::resolvedCount).sum();
        double weightedSum = rows.stream()
                .mapToDouble(r -> r.avgResolutionMinutes() * r.resolvedCount()).sum();
        return count == 0 ? 0.0 : weightedSum / count;
    }
}
