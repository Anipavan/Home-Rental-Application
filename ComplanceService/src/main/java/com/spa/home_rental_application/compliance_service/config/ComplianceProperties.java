package com.spa.home_rental_application.compliance_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Externalised compliance / GST configuration. Bound from {@code app.compliance.*}.
 */
@ConfigurationProperties(prefix = "app.compliance")
@Getter
@Setter
public class ComplianceProperties {

    /** Annualised rent threshold above which residential GST applies (₹). */
    private BigDecimal gstAnnualRentThreshold = new BigDecimal("2000000.00");

    /** GST rate percent (CGST + SGST combined). */
    private BigDecimal gstRatePercent = new BigDecimal("18.00");

    /** Prefix for invoice numbers ({prefix}-{yyyy}-{nnnnnn}). */
    private String invoicePrefix = "RG";

    /** Local filesystem dir to drop generated invoice PDFs (S3 in prod). */
    private String invoiceStorageDir = "uploads/gst-invoices";

    private Rera rera = new Rera();

    @Getter @Setter
    public static class Rera {
        /** Active RERA portal adapter: MOCK | KARNATAKA | MAHARASHTRA. */
        private String provider = "MOCK";
        private int registrationValidityYears = 5;
    }
}
