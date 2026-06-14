package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;

/**
 * Result of POST /society/{buildingId}/charges/initiate-payment.
 * Carries everything the FE needs to navigate the user to the existing
 * rent-pay route — paymentId for the URL, totalAmount for a quick
 * confirmation toast, and collectionCount so the success view can
 * say "3 charges paid" instead of just "1 payment received".
 */
public record SocietyChargePaymentInitiatedResponse(
        String paymentId,
        BigDecimal totalAmount,
        int collectionCount
) {}
