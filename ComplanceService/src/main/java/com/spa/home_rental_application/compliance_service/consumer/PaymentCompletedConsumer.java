package com.spa.home_rental_application.compliance_service.consumer;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent;
import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateGstInvoiceRequest;
import com.spa.home_rental_application.compliance_service.Exceptionclass.InvoiceAlreadyExistsException;
import com.spa.home_rental_application.compliance_service.service.GstInvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Auto-generates a GST invoice every time a payment is captured. This is
 * idempotent — the unique constraint on {@code payment_id} prevents
 * duplicates, and we swallow {@link InvoiceAlreadyExistsException}.
 */
@Component
@Slf4j
public class PaymentCompletedConsumer {

    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    private final GstInvoiceService gstInvoiceService;

    public PaymentCompletedConsumer(GstInvoiceService gstInvoiceService) {
        this.gstInvoiceService = gstInvoiceService;
    }

    @KafkaListener(
            topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "hra-compliance-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event == null || event.getPaymentId() == null) {
            log.warn("Ignoring payment-events message with null paymentId");
            return;
        }
        if (event.getEventType() != null
                && !"payment.completed".equalsIgnoreCase(event.getEventType())) {
            log.debug("Skipping payment-event type={}", event.getEventType());
            return;
        }
        try {
            GenerateGstInvoiceRequest request = new GenerateGstInvoiceRequest(
                    event.getTenantId(),
                    event.getOwnerId(),
                    event.getAmount(),
                    event.getAmount() == null ? BigDecimal.ZERO
                            : event.getAmount().multiply(MONTHS_PER_YEAR),
                    event.getPaidDate() == null ? LocalDate.now()
                            : event.getPaidDate().atZone(ZoneId.systemDefault()).toLocalDate());
            gstInvoiceService.generate(event.getPaymentId(), request);
            log.info("Auto-generated GST invoice for paymentId={}", event.getPaymentId());
        } catch (InvoiceAlreadyExistsException dup) {
            log.info("Invoice already exists for paymentId={} — skipping (idempotent)",
                    event.getPaymentId());
        } catch (Exception ex) {
            log.error("Failed to auto-generate invoice for paymentId={}",
                    event.getPaymentId(), ex);
            // Swallow — manual /compliance/gst/generate/{paymentId} is the recovery path
        }
    }
}
