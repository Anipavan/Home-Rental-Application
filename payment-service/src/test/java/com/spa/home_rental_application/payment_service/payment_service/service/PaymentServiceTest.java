package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.*;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentAlreadyPaidException;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentGatewayException;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentInitiationResult;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentVerificationResult;
import com.spa.home_rental_application.payment_service.payment_service.repository.InvoiceRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ReceiptRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock ReceiptRepository receiptRepo;
    @Mock com.spa.home_rental_application.payment_service.payment_service.repository.ProcessedWebhookRepository webhookRepo;
    @Mock PaymentGateway gateway;
    @Mock PaymentServiceEvents events;
    @Mock com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient propertyClient;
    @Mock com.spa.home_rental_application.payment_service.payment_service.client.UserClient userClient;
    @Mock com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher audit;

    PaymentServiceImpl service() {
        return new PaymentServiceImpl(paymentRepo, invoiceRepo, receiptRepo, webhookRepo, gateway, events,
                new PaymentProperties(),
                new com.spa.home_rental_application.payment_service.payment_service.service.impl.PaymentPdfGenerator(),
                propertyClient,
                userClient,
                audit);
    }

    @Test
    void createPayment_persists_andPublishesPaymentCreated() {
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId("PAY-1");
            return p;
        });
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new CreatePaymentRequest("T1", "F1", "O1",
                new BigDecimal("8500.00"), LocalDate.of(2026, 5, 1));
        PaymentResponse r = service().createPayment(req);

        assertThat(r.id()).isEqualTo("PAY-1");
        assertThat(r.status()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<PaymentCreatedEvent> evt = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        verify(events).sendPaymentCreated(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("payment.created");
        assertThat(evt.getValue().getAmount()).isEqualByComparingTo("8500.00");
    }

    @Test
    void verifyPayment_success_marksPaid_andPublishesCompleted() {
        Payment p = Payment.builder().id("PAY-1").tenantId("T1").ownerId("O1")
                .amount(new BigDecimal("8500"))
                .totalAmount(new BigDecimal("8500"))
                .status(PaymentStatus.PROCESSING)
                .paymentMethod(PaymentMethod.UPI)
                .build();
        when(paymentRepo.findById("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptRepo.findByPaymentId("PAY-1")).thenReturn(Optional.empty());
        when(receiptRepo.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.verify(any(), any())).thenReturn(PaymentVerificationResult.builder()
                .success(true).transactionId("txn_abc").build());

        var req = new VerifyPaymentRequest("PAY-1", "order_1", "txn_abc", "sig_xxx");
        PaymentResponse r = service().verifyPayment(req);

        assertThat(r.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(r.transactionId()).isEqualTo("txn_abc");

        ArgumentCaptor<PaymentCompletedEvent> evt = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(events).sendPaymentCompleted(evt.capture());
        assertThat(evt.getValue().getPaymentMethod()).isEqualTo("UPI");
    }

    @Test
    void verifyPayment_gatewayFailure_publishesFailed_andThrows() {
        Payment p = Payment.builder().id("PAY-1").tenantId("T1")
                .amount(BigDecimal.TEN).totalAmount(BigDecimal.TEN)
                .status(PaymentStatus.PROCESSING).build();
        when(paymentRepo.findById("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.verify(any(), any())).thenReturn(PaymentVerificationResult.builder()
                .success(false).failureReason("declined").gatewayErrorCode("E_DECLINED").build());

        assertThatThrownBy(() -> service().verifyPayment(new VerifyPaymentRequest("PAY-1", "o", "t", "s")))
                .isInstanceOf(PaymentGatewayException.class);
        verify(events).sendPaymentFailed(any(PaymentFailedEvent.class));
        verify(events, never()).sendPaymentCompleted(any());
    }

    @Test
    void payCash_marksPaid_evenWithoutGateway() {
        Payment p = Payment.builder().id("PAY-1").tenantId("T1")
                .amount(BigDecimal.TEN).totalAmount(BigDecimal.TEN)
                .status(PaymentStatus.PENDING).build();
        when(paymentRepo.findById("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptRepo.findByPaymentId("PAY-1")).thenReturn(Optional.empty());
        when(receiptRepo.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse r = service().payCash("PAY-1", new PayCashRequest("O1", "RECPT-001"));

        assertThat(r.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(r.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        verify(events).sendPaymentCompleted(any());
    }

    @Test
    void initiatePayment_alreadyPaid_throws() {
        Payment p = Payment.builder().id("PAY-1").status(PaymentStatus.PAID).build();
        when(paymentRepo.findById("PAY-1")).thenReturn(Optional.of(p));
        var req = new InitiatePaymentRequest("PAY-1", PaymentMethod.UPI,
                null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service().initiatePayment(req))
                .isInstanceOf(PaymentAlreadyPaidException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void onFlatOccupied_seedsFirstPayment_whenNoActiveExists() {
        when(paymentRepo.findByFlatIdAndStatusIn(eq("F1"), any())).thenReturn(List.of());
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId("PAY-NEW");
            return p;
        });
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onFlatOccupied("F1", "T1", new BigDecimal("8500"), LocalDate.of(2026, 5, 1));

        verify(paymentRepo).save(any(Payment.class));
        verify(events).sendPaymentCreated(any());
    }

    @Test
    void onFlatVacated_cancelsActivePayments() {
        Payment a = Payment.builder().id("P1").flatId("F1").status(PaymentStatus.PENDING).build();
        Payment b = Payment.builder().id("P2").flatId("F1").status(PaymentStatus.OVERDUE).build();
        when(paymentRepo.findByFlatIdAndStatusIn(eq("F1"), any())).thenReturn(List.of(a, b));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service().onFlatVacated("F1", "T1");

        assertThat(a.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(b.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
