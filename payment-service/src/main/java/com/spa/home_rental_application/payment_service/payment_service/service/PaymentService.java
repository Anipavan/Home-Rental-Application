package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.*;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentService {

    /* --- lifecycle --- */
    PaymentResponse createPayment(CreatePaymentRequest dto);
    PaymentResponse getPaymentById(String id);
    Page<PaymentResponse> getAllPayments(Pageable pageable);

    /* --- lookups --- */
    List<PaymentResponse> getPaymentsByTenant(String tenantId);
    List<PaymentResponse> getPaymentsByOwner(String ownerId);
    List<PaymentResponse> getOverduePayments();

    /* --- pay --- */
    InitiatePaymentResponse initiatePayment(InitiatePaymentRequest dto);
    PaymentResponse verifyPayment(VerifyPaymentRequest dto);
    PaymentResponse payCash(String paymentId, PayCashRequest body);

    /* --- documents --- */
    InvoiceResponse getInvoice(String paymentId);
    ReceiptResponse getReceipt(String paymentId);
    byte[] getInvoicePdf(String paymentId);
    byte[] getReceiptPdf(String paymentId);

    /* --- analytics --- */
    PaymentStatsResponse getStatsByTenant(String tenantId);
    PaymentStatsResponse getStatsByOwner(String ownerId);

    /* --- cross-service consumers --- */
    void onFlatOccupied(String flatId, String tenantId, BigDecimal rentAmount, LocalDate leaseStartDate);
    void onFlatVacated(String flatId, String tenantId);
}
