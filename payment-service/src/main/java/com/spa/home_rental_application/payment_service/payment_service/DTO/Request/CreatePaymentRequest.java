package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for POST /payments — creates a new invoice/payment record manually. */
public record CreatePaymentRequest(
        @NotBlank(message = "tenantId is mandatory") String tenantId,
        @NotBlank(message = "flatId is mandatory")   String flatId,
        String ownerId,
        @NotNull(message = "amount is mandatory") @Positive BigDecimal amount,
        @NotNull(message = "dueDate is mandatory") LocalDate dueDate
) {}
