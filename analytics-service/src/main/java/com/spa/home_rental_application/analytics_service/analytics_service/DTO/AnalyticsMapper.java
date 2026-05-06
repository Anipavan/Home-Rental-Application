package com.spa.home_rental_application.analytics_service.analytics_service.DTO;

import com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response.*;
import com.spa.home_rental_application.analytics_service.analytics_service.entities.*;

public final class AnalyticsMapper {

    private AnalyticsMapper() {}

    public static RevenueResponse toResponse(RevenueSummary r) {
        if (r == null) return null;
        return new RevenueResponse(
                r.getOwnerId(), r.getYear(), r.getMonth(),
                r.getTotalRevenue(), r.getTotalPaid(),
                r.getTotalPending(), r.getTotalOverdue(),
                r.getPaymentCount(), r.getGeneratedAt()
        );
    }

    public static OccupancyResponse toResponse(OccupancyStat o) {
        if (o == null) return null;
        return new OccupancyResponse(
                o.getBuildingId(), o.getStatDate(),
                o.getTotalFlats(), o.getOccupiedFlats(), o.getVacantFlats(),
                o.getOccupancyRate()
        );
    }

    public static PaymentTrendResponse toResponse(PaymentTrend t) {
        if (t == null) return null;
        return new PaymentTrendResponse(
                t.getOwnerId(), t.getYear(), t.getMonth(),
                t.getOnTimePayments(), t.getLatePayments(),
                t.getAvgDelayDays(), t.getCollectionRate()
        );
    }

    public static MaintenanceMetricResponse toResponse(MaintenanceMetric m) {
        if (m == null) return null;
        return new MaintenanceMetricResponse(
                m.getCategory(), m.getResolvedCount(), m.getAvgResolutionMinutes()
        );
    }
}
