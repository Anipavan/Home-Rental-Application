package com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Published when a GST invoice has been generated for a paid rental month.
 * Notification Service forwards this to the tenant via WhatsApp / Email.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GstInvoiceGeneratedEvent {
    private String eventType;
    private String invoiceId;
    private String paymentId;
    private String tenantId;
    private String ownerId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal rentAmount;
    private Boolean gstApplicable;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String pdfUrl;
    private LocalDateTime timestamp;
}
