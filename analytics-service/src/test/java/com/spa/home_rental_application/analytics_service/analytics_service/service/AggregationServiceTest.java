package com.spa.home_rental_application.analytics_service.analytics_service.service;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.MaintenanceMetric;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.OccupancyStat;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.PaymentTrend;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.RevenueSummary;
import com.spa.home_rental_application.analytics_service.analytics_service.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock RevenueSummaryRepository revenueRepo;
    @Mock OccupancyStatRepository occupancyRepo;
    @Mock PaymentTrendRepository trendRepo;
    @Mock MaintenanceMetricRepository maintenanceRepo;

    AggregationService service() {
        return new AggregationService(revenueRepo, occupancyRepo, trendRepo, maintenanceRepo);
    }

    @Test
    void onPaymentCompleted_incrementsRevenueAndCountsOnTime() {
        when(revenueRepo.findByOwnerIdAndYearAndMonth("O1", 2026, 5)).thenReturn(Optional.empty());
        when(revenueRepo.save(any(RevenueSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(trendRepo.findByOwnerIdAndYearAndMonth("O1", 2026, 5)).thenReturn(Optional.empty());
        when(trendRepo.save(any(PaymentTrend.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onPaymentCompleted("O1", new BigDecimal("8500"),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));

        ArgumentCaptor<RevenueSummary> rev = ArgumentCaptor.forClass(RevenueSummary.class);
        verify(revenueRepo).save(rev.capture());
        assertThat(rev.getValue().getTotalPaid()).isEqualByComparingTo("8500");
        assertThat(rev.getValue().getPaymentCount()).isEqualTo(1);

        ArgumentCaptor<PaymentTrend> trend = ArgumentCaptor.forClass(PaymentTrend.class);
        verify(trendRepo).save(trend.capture());
        assertThat(trend.getValue().getOnTimePayments()).isEqualTo(1);
        assertThat(trend.getValue().getLatePayments()).isZero();
    }

    @Test
    void onPaymentCompleted_lateBumpsLateCountAndDelay() {
        when(revenueRepo.findByOwnerIdAndYearAndMonth("O1", 2026, 5)).thenReturn(Optional.empty());
        when(revenueRepo.save(any(RevenueSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(trendRepo.findByOwnerIdAndYearAndMonth("O1", 2026, 5)).thenReturn(Optional.empty());
        when(trendRepo.save(any(PaymentTrend.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onPaymentCompleted("O1", new BigDecimal("8500"),
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 1));

        ArgumentCaptor<PaymentTrend> trend = ArgumentCaptor.forClass(PaymentTrend.class);
        verify(trendRepo).save(trend.capture());
        assertThat(trend.getValue().getLatePayments()).isEqualTo(1);
        assertThat(trend.getValue().getTotalDelayDays()).isEqualTo(7);
        assertThat(trend.getValue().getOnTimePayments()).isZero();
    }

    @Test
    void onFlatOccupied_incrementsOccupiedCount() {
        when(occupancyRepo.findByBuildingIdAndStatDate(eq("B1"), any())).thenReturn(Optional.empty());
        when(occupancyRepo.save(any(OccupancyStat.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onFlatOccupied("B1");

        ArgumentCaptor<OccupancyStat> cap = ArgumentCaptor.forClass(OccupancyStat.class);
        verify(occupancyRepo).save(cap.capture());
        assertThat(cap.getValue().getOccupiedFlats()).isEqualTo(1);
        assertThat(cap.getValue().getOccupancyRate()).isEqualTo(1.0);
    }

    @Test
    void onMaintenanceResolved_aggregatesByCategory() {
        when(maintenanceRepo.findByCategory("PLUMBING")).thenReturn(Optional.empty());
        when(maintenanceRepo.save(any(MaintenanceMetric.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onMaintenanceResolved("PLUMBING", 90);

        ArgumentCaptor<MaintenanceMetric> cap = ArgumentCaptor.forClass(MaintenanceMetric.class);
        verify(maintenanceRepo).save(cap.capture());
        assertThat(cap.getValue().getResolvedCount()).isEqualTo(1);
        assertThat(cap.getValue().getTotalResolutionMinutes()).isEqualTo(90);
        assertThat(cap.getValue().getAvgResolutionMinutes()).isEqualTo(90.0);
    }
}
