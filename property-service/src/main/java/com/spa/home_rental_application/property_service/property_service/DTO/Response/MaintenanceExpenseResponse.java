package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record MaintenanceExpenseResponse(
        String id,
        String buildingId,
        String expenseMonth,
        ExpenseCategory category,
        String subcategory,
        BigDecimal amount,
        String vendorName,
        LocalDate paidOnDate,
        String receiptDocId,
        String notes,
        String addedByUserId,
        LocalDateTime addedAt
) {
}
