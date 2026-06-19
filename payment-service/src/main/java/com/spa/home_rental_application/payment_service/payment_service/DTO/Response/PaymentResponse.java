package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import com.spa.home_rental_application.payment_service.payment_service.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentResponse(
        String id,
        String tenantId,
        String flatId,
        String ownerId,
        BigDecimal amount,
        BigDecimal lateFee,
        BigDecimal totalAmount,
        LocalDate dueDate,
        Instant   paymentDate,
        PaymentStatus  status,
        PaymentMethod  paymentMethod,
        UpiApp         upiApp,
        WalletProvider walletProvider,
        CardNetwork    cardNetwork,
        String         cardLast4,
        String         upiVpa,
        String         transactionId,
        String         gatewayOrderId,
        String         gatewayName,
        String         failureReason,
        /**
         * What this payment is for — drives the Rent | Maintenance
         * tab split on the tenant Payments page. Values: RENT (default,
         * monthly scheduler-fed invoices), SOCIETY_CHARGE
         * (resident-initiated bulk-pay / per-charge society payment).
         */
        String         sourceType,
        Instant        createdAt,
        Instant        updatedAt
) {}
