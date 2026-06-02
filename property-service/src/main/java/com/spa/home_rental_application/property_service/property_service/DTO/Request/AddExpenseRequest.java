package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import com.spa.home_rental_application.property_service.property_service.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddExpenseRequest(
        @NotNull
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
                message = "expenseMonth must be YYYY-MM")
        String expenseMonth,

        @NotNull
        ExpenseCategory category,

        @Size(max = 100)
        String subcategory,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be > 0")
        BigDecimal amount,

        @Size(max = 200)
        String vendorName,

        @NotNull
        LocalDate paidOnDate,

        /** Optional document-service blob id of an uploaded bill / receipt. */
        @Size(max = 36)
        String receiptDocId,

        @Size(max = 1000)
        String notes
) {
}
