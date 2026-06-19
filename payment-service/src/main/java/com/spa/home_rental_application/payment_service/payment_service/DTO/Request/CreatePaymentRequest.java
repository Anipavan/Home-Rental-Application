package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for POST /payments — creates a new invoice/payment record manually.
 *
 * <p>{@code sourceType} is optional; defaults to "RENT" server-side when
 * absent. Set it to "SOCIETY_CHARGE" via POST /payments/society-charge
 * so the FE can split Rent / Maintenance tabs on the Payments page.
 * Any new payment category in future (DEPOSIT, etc.) just adds another
 * string here — no DTO change needed.
 */
public record CreatePaymentRequest(
        @NotBlank(message = "tenantId is mandatory") String tenantId,
        @NotBlank(message = "flatId is mandatory")   String flatId,
        String ownerId,
        @NotNull(message = "amount is mandatory") @Positive BigDecimal amount,
        @NotNull(message = "dueDate is mandatory") LocalDate dueDate,
        String sourceType
) {}
