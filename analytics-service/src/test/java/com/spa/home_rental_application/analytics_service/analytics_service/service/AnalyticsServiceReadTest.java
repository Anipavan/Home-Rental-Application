package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.MaintenanceMetric;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.OccupancyStat;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.PaymentTrend;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.RevenueSummary;
import com.spa.home_rental_application.analytics_service.analytics_service.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceReadTest {

    @Mock RevenueSummaryRepository revenueRepo;
    @Mock OccupancyStatRepository occupancyRepo;
    @Mock PaymentTrendRepository trendRepo;
    @Mock MaintenanceMetricRepository maintenanceRepo;

    AnalyticsService service() {
        return new AnalyticsService(revenueRepo, occupancyRepo, trendRepo, maintenanceRepo);
    }

    @Test
    void yearlyTotalForOwner_sumsAcrossMonths() {
        when(revenueRepo.findByOwnerId("O1")).thenReturn(List.of(
                RevenueSummary.builder().ownerId("O1").year(2026).month(1)
                        .totalPaid(new BigDecimal("8500")).paymentCount(1).build(),
                RevenueSummary.builder().ownerId("O1").year(2026).month(2)
                        .totalPaid(new BigDecimal("8500")).paymentCount(1).build(),
                RevenueSummary.builder().ownerId("O1").year(2025).month(12) // different year, ignored
                        .totalPaid(new BigDecimal("99999")).paymentCount(1).build()
        ));
        var resp = service().yearlyTotalForOwner("O1", 2026);
        assertThat(resp.totalPaid()).isEqualByComparingTo("17000");
        assertThat(resp.paymentCount()).isEqualTo(2);
    }

    @Test
    void compareMonthOverMonth_handlesPriorZero() {
        // Current month present, previous absent → previous=0, pct should be null
        when(revenueRepo.findByOwnerIdAndYearAndMonth(eq("O1"), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        var resp = service().compareMonthOverMonth("O1");
        assertThat(resp.previousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.percentDelta()).isNull();
    }

    @Test
    void overallOccupancyForToday_aggregatesAcrossBuildings() {
        when(occupancyRepo.findByStatDate(LocalDate.now())).thenReturn(List.of(
                OccupancyStat.builder().buildingId("B1").totalFlats(10).occupiedFlats(8).build(),
                OccupancyStat.builder().buildingId("B2").totalFlats(20).occupiedFlats(15).build()
        ));
        var r = service().overallOccupancyForToday();
        assertThat(r.totalFlats()).isEqualTo(30);
        assertThat(r.occupiedFlats()).isEqualTo(23);
        assertThat(r.vacantFlats()).isEqualTo(7);
        assertThat(r.occupancyRate()).isEqualTo(23.0 / 30.0, within(0.0001));
    }

    @Test
    void overallCollectionRateForOwner_zeroPayments_returnsOne() {
        when(trendRepo.findByOwnerIdOrderByYearAscMonthAsc("O1")).thenReturn(List.of());
        assertThat(service().overallCollectionRateForOwner("O1")).isEqualTo(1.0);
    }

    @Test
    void overallCollectionRateForOwner_mixed() {
        when(trendRepo.findByOwnerIdOrderByYearAscMonthAsc("O1")).thenReturn(List.of(
                PaymentTrend.builder().ownerId("O1").onTimePayments(7).latePayments(3).build()));
        assertThat(service().overallCollectionRateForOwner("O1")).isEqualTo(0.7);
    }

    @Test
    void averageResolutionMinutes_weightedByCategoryCount() {
        when(maintenanceRepo.findAll()).thenReturn(List.of(
                MaintenanceMetric.builder().category("PLUMBING").resolvedCount(2).totalResolutionMinutes(120).build(),
                MaintenanceMetric.builder().category("ELECTRICAL").resolvedCount(3).totalResolutionMinutes(60).build()
        ));
        // weighted avg = (60*2 + 20*3) / (2+3) = (120+60)/5 = 36
        assertThat(service().averageResolutionMinutes()).isEqualTo(36.0, within(0.001));
    }

    private static <T> T eq(T t) { return org.mockito.ArgumentMatchers.eq(t); }
}
