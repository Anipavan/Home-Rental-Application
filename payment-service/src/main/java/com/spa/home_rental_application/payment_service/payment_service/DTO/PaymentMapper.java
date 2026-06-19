package com.spa.home_rental_application.payment_service.payment_service.DTO;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.InvoiceResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.ReceiptResponse;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;

public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(Payment p) {
        if (p == null) return null;
        return new PaymentResponse(
                p.getId(),
                p.getTenantId(),
                p.getFlatId(),
                p.getOwnerId(),
                p.getAmount(),
                p.getLateFee(),
                p.getTotalAmount(),
                p.getDueDate(),
                p.getPaymentDate(),
                p.getStatus(),
                p.getPaymentMethod(),
                p.getUpiApp(),
                p.getWalletProvider(),
                p.getCardNetwork(),
                p.getCardLast4(),
                p.getUpiVpa(),
                p.getTransactionId(),
                p.getGatewayOrderId(),
                p.getGatewayName(),
                p.getFailureReason(),
                p.getSourceType(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    public static InvoiceResponse toResponse(Invoice i) {
        if (i == null) return null;
        return new InvoiceResponse(
                i.getId(), i.getPaymentId(), i.getInvoiceNumber(),
                i.getGeneratedDate(), i.getPdfUrl()
        );
    }

    public static ReceiptResponse toResponse(Receipt r) {
        if (r == null) return null;
        return new ReceiptResponse(
                r.getId(), r.getPaymentId(), r.getReceiptNumber(),
                r.getGeneratedDate(), r.getPdfUrl()
        );
    }
}
