package com.spa.home_rental_application.compliance_service.mapper;

import com.spa.home_rental_application.compliance_service.DTO.Response.GstInvoiceResponseDto;
import com.spa.home_rental_application.compliance_service.DTO.Response.ReraRegistrationResponseDto;
import com.spa.home_rental_application.compliance_service.Entities.GstInvoice;
import com.spa.home_rental_application.compliance_service.Entities.ReraRegistration;
import org.springframework.stereotype.Component;

@Component
public class ComplianceMapper {

    public ReraRegistrationResponseDto toReraResponse(ReraRegistration r) {
        if (r == null) return null;
        return new ReraRegistrationResponseDto(
                r.getId(),
                r.getPropertyId(),
                r.getOwnerId(),
                r.getState(),
                r.getReraRegistrationNumber(),
                r.getReraPortalId(),
                r.getRegistrationStatus(),
                r.getRegisteredAt(),
                r.getExpiryDate(),
                r.getFailureReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    public GstInvoiceResponseDto toGstResponse(GstInvoice i) {
        if (i == null) return null;
        return new GstInvoiceResponseDto(
                i.getId(),
                i.getPaymentId(),
                i.getTenantId(),
                i.getOwnerId(),
                i.getInvoiceNumber(),
                i.getInvoiceDate(),
                i.getRentAmount(),
                i.getGstApplicable(),
                i.getGstRatePercent(),
                i.getGstAmount(),
                i.getTotalAmount(),
                i.getPdfUrl(),
                i.getSentViaWhatsapp(),
                i.getCreatedAt()
        );
    }
}
