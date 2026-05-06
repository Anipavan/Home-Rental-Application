package com.spa.home_rental_application.analytics_service.analytics_service.DTO.Response;

public record PaymentTrendResponse(
        String ownerId,
        int year,
        int month,
        long onTimePayments,
        long latePayments,
        double avgDelayDays,
        double collectionRate
) {}
