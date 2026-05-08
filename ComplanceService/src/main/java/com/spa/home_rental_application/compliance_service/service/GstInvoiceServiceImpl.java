package com.spa.home_rental_application.compliance_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ComplianceServiceEvents.GstInvoiceGeneratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.ComplianceServiceEvents;
import com.spa.home_rental_application.compliance_service.DTO.Request.GenerateGstInvoiceRequest;
import com.spa.home_rental_application.compliance_service.DTO.Response.GstInvoiceResponseDto;
import com.spa.home_rental_application.compliance_service.Entities.GstInvoice;
import com.spa.home_rental_application.compliance_service.Exceptionclass.GstInvoiceNotFoundException;
import com.spa.home_rental_application.compliance_service.Exceptionclass.InvoiceAlreadyExistsException;
import com.spa.home_rental_application.compliance_service.config.ComplianceProperties;
import com.spa.home_rental_application.compliance_service.mapper.ComplianceMapper;
import com.spa.home_rental_application.compliance_service.repository.GstInvoiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

@Service
@Slf4j
public class GstInvoiceServiceImpl implements GstInvoiceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GstInvoiceRepository repository;
    private final ComplianceMapper mapper;
    private final ComplianceProperties props;
    private final InvoicePdfGenerator pdfGenerator;
    private final ComplianceServiceEvents events;

    public GstInvoiceServiceImpl(GstInvoiceRepository repository,
                                 ComplianceMapper mapper,
                                 ComplianceProperties props,
                                 InvoicePdfGenerator pdfGenerator,
                                 ComplianceServiceEvents events) {
        this.repository = repository;
        this.mapper = mapper;
        this.props = props;
        this.pdfGenerator = pdfGenerator;
        this.events = events;
    }

    @Override
    @Transactional
    public GstInvoiceResponseDto generate(String paymentId, GenerateGstInvoiceRequest request) {
        log.info("Generate GST invoice paymentId={} owner={} tenant={} rent={}",
                paymentId, request.ownerId(), request.tenantId(), request.rentAmount());

        if (repository.existsByPaymentId(paymentId)) {
            throw new InvoiceAlreadyExistsException(
                    "Invoice already exists for paymentId=" + paymentId);
        }

        boolean gstApplicable = request.annualRentEstimate() != null
                && request.annualRentEstimate().compareTo(props.getGstAnnualRentThreshold()) > 0;

        BigDecimal rate = gstApplicable ? props.getGstRatePercent() : BigDecimal.ZERO;
        BigDecimal gstAmount = gstApplicable
                ? request.rentAmount().multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal total = request.rentAmount().add(gstAmount);

        LocalDate invoiceDate = request.invoiceDate() != null ? request.invoiceDate() : LocalDate.now();

        GstInvoice invoice = GstInvoice.builder()
                .paymentId(paymentId)
                .tenantId(request.tenantId())
                .ownerId(request.ownerId())
                .invoiceNumber(nextInvoiceNumber())
                .invoiceDate(invoiceDate)
                .rentAmount(request.rentAmount())
                .gstApplicable(gstApplicable)
                .gstRatePercent(gstApplicable ? rate : null)
                .gstAmount(gstApplicable ? gstAmount : null)
                .totalAmount(total)
                .build();

        GstInvoice saved = repository.save(invoice);

        // Generate PDF after save so we have a stable invoice number on disk.
        String pdfPath = pdfGenerator.generate(saved);
        saved.setPdfUrl(pdfPath);
        repository.save(saved);

        events.sendGstInvoiceGenerated(GstInvoiceGeneratedEvent.builder()
                .eventType("gst.invoice.generated")
                .invoiceId(saved.getId())
                .paymentId(saved.getPaymentId())
                .tenantId(saved.getTenantId())
                .ownerId(saved.getOwnerId())
                .invoiceNumber(saved.getInvoiceNumber())
                .invoiceDate(saved.getInvoiceDate())
                .rentAmount(saved.getRentAmount())
                .gstApplicable(saved.getGstApplicable())
                .gstAmount(saved.getGstAmount())
                .totalAmount(saved.getTotalAmount())
                .pdfUrl(saved.getPdfUrl())
                .timestamp(LocalDateTime.now())
                .build());

        return mapper.toGstResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GstInvoiceResponseDto getById(String invoiceId) {
        return repository.findById(invoiceId)
                .map(mapper::toGstResponse)
                .orElseThrow(() -> new GstInvoiceNotFoundException(
                        "No GST invoice with id=" + invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getPdf(String invoiceId) throws IOException {
        GstInvoice invoice = repository.findById(invoiceId)
                .orElseThrow(() -> new GstInvoiceNotFoundException(
                        "No GST invoice with id=" + invoiceId));
        if (invoice.getPdfUrl() == null) {
            throw new GstInvoiceNotFoundException(
                    "Invoice " + invoiceId + " has no rendered PDF on disk");
        }
        return Files.readAllBytes(Paths.get(invoice.getPdfUrl()));
    }

    /**
     * Format: {prefix}-{yyyy}-{6-digit running counter for the current year}.
     * Counter is the count of rows already present + 1; gaps tolerated.
     */
    private String nextInvoiceNumber() {
        int year = Year.now().getValue();
        long count = repository.count() + 1;
        return String.format("%s-%d-%06d", props.getInvoicePrefix(), year, count);
    }
}
