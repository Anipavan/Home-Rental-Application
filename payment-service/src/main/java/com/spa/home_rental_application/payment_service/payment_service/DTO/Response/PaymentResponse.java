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
        Instant        createdAt,
        Instant        updatedAt
) {}
