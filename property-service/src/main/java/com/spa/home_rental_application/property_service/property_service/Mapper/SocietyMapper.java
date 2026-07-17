package com.spa.home_rental_application.property_service.property_service.Mapper;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.MaintenanceExpenseResponse;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SocietyConfigResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceExpense;
import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Entity ↔ DTO conversions for the society feature. Lives as a
 * Spring component so the frontend-base URL ({@code app.frontend-url})
 * can be injected — we synthesise the full shareable URL for the
 * {@link SocietyConfigResponse#publicViewUrl()} so the owner can
 * copy-paste it straight into a WhatsApp group.
 */
@Component
public class SocietyMapper {

    private final String frontendBaseUrl;

    public SocietyMapper(
            @Value("${app.frontend-url:${FRONTEND_URL:https://anirudhhomes.in}}")
            String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "https://anirudhhomes.in"
                : frontendBaseUrl.replaceAll("/+$", "");
    }

    public SocietyConfigResponse toResponse(SocietyConfig e) {
        if (e == null) return null;
        return SocietyConfigResponse.builder()
                .id(e.getId())
                .buildingId(e.getBuildingId())
                .monthlyDueDay(e.getMonthlyDueDay())
                .defaultPerFlatAmount(e.getDefaultPerFlatAmount())
                .maintainerUserId(e.getMaintainerUserId())
                .publicViewToken(e.getPublicViewToken())
                .publicViewUrl(frontendBaseUrl + "/society/view/" + e.getPublicViewToken())
                .societyDisplayName(e.getSocietyDisplayName())
                .upiId(e.getUpiId())
                .payeeName(e.getPayeeName())
                .accountNumber(e.getAccountNumber())
                .ifscCode(e.getIfscCode())
                .bankConfigFlaggedAt(e.getBankConfigFlaggedAt())
                .bankConfigFlagReports(e.getBankConfigFlagReports())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public MaintenanceExpenseResponse toResponse(MaintenanceExpense e) {
        if (e == null) return null;
        return MaintenanceExpenseResponse.builder()
                .id(e.getId())
                .buildingId(e.getBuildingId())
                .expenseMonth(e.getExpenseMonth())
                .category(e.getCategory())
                .subcategory(e.getSubcategory())
                .amount(e.getAmount())
                .vendorName(e.getVendorName())
                .paidOnDate(e.getPaidOnDate())
                .receiptDocId(e.getReceiptDocId())
                .notes(e.getNotes())
                .addedByUserId(e.getAddedByUserId())
                .addedAt(e.getAddedAt())
                .build();
    }
}
