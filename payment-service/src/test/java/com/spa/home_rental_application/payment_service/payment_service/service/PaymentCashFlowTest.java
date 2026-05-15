package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentCompletedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.entities.Invoice;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentAlreadyPaidException;
import com.spa.home_rental_application.payment_service.payment_service.exception.PaymentNotFoundException;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.repository.InvoiceRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ProcessedWebhookRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ReceiptRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.impl.PaymentPdfGenerator;
import com.spa.home_rental_application.payment_service.payment_service.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-15d: payCash() flow coverage for PaymentServiceImpl.
 *
 *  POSITIVE
 *   - payCash transitions PENDING → PAID, sets method=CASH, gateway=manual,
 *     clears gatewayOrderId, and emits both payment.completed (Kafka) +
 *     payment.cash.recorded (audit).
 *   - With reference supplied, transactionId == the reference.
 *   - Without reference, transactionId is auto-generated as "CASH-<uuid>".
 *   - Works on OVERDUE rows (audit allows the late path explicitly).
 *
 *  NEGATIVE
 *   - payCash on already-PAID throws PaymentAlreadyPaidException.
 *   - payCash on CANCELLED throws PaymentAlreadyPaidException.
 *   - payCash on unknown id throws PaymentNotFoundException.
 *   - On exception, no payment.completed event is fired, no audit row
 *     is published (state stays clean).
 */
@ExtendWith(MockitoExtension.class)
class PaymentCashFlowTest {

    @Mock PaymentRepository paymentRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock ReceiptRepository receiptRepo;
    @Mock ProcessedWebhookRepository webhookRepo;
    @Mock PaymentGateway gateway;
    @Mock PaymentServiceEvents events;
    @Mock PropertyClient propertyClient;
    @Mock com.spa.home_rental_application.payment_service.payment_service.client.UserClient userClient;
    @Mock AuditEventPublisher audit;

    private PaymentServiceImpl service() {
        return new PaymentServiceImpl(paymentRepo, invoiceRepo, receiptRepo, webhookRepo, gateway, events,
                new PaymentProperties(), new PaymentPdfGenerator(), propertyClient, userClient, audit);
    }

    private Payment pending(String id) {
        return Payment.builder()
                .id(id).tenantId("T-1").flatId("F-1").ownerId("O-1")
                .amount(new BigDecimal("15000"))
                .lateFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("15000"))
                .dueDate(LocalDate.of(2026, 6, 1))
                .status(PaymentStatus.PENDING)
                .build();
    }

    private void stubReceiptInfra() {
        // markPaid() generates a Receipt via the receiptRepo. Mock the
        // save so we don't NPE on a non-routed save() during the
        // success path. invoiceRepo is unused on this code path
        // (invoice is generated at createPayment-time, not at
        // markPaid-time), so no stubbing needed there.
        lenient().when(receiptRepo.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /* ───────────────────────── POSITIVE ───────────────────────── */

    @Test
    @DisplayName("[+] payCash on PENDING: PAID + CASH + manual gateway + audit + payment.completed")
    void cash_pending_marks_paid() {
        stubReceiptInfra();
        Payment p = pending("P-CASH-1");
        when(paymentRepo.findById(eq("P-CASH-1"))).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse resp = service().payCash("P-CASH-1",
                new PayCashRequest("O-1", "CHQ-9876"));

        assertThat(resp.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(resp.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(resp.gatewayName()).isEqualTo("manual");
        assertThat(resp.transactionId()).isEqualTo("CHQ-9876");

        // Audit row: payment.cash.recorded with owner as actor
        verify(audit).publishSuccess(
                eq("payment.cash.recorded"),
                eq("O-1"),    // actor = owner
                eq("T-1"),    // subject = tenant
                eq("P-CASH-1"),   // resourceId = payment id
                any());

        // payment.completed Kafka event with the right shape
        ArgumentCaptor<PaymentCompletedEvent> evt = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(events).sendPaymentCompleted(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("payment.completed");
        assertThat(evt.getValue().getPaymentMethod()).isEqualTo("CASH");
        assertThat(evt.getValue().getTransactionId()).isEqualTo("CHQ-9876");
    }

    @Test
    @DisplayName("[+] payCash with no reference auto-generates a CASH-<uuid> transaction id")
    void cash_without_reference_auto_generates_txn_id() {
        stubReceiptInfra();
        Payment p = pending("P-CASH-2");
        when(paymentRepo.findById(eq("P-CASH-2"))).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse resp = service().payCash("P-CASH-2",
                new PayCashRequest("O-1", null));

        assertThat(resp.transactionId()).startsWith("CASH-");
        assertThat(resp.transactionId().length()).isGreaterThan(5);

        // gatewayOrderId must be CLEARED on the cash path so the
        // Razorpay reconciliation flow doesn't pick up a stale order.
        ArgumentCaptor<Payment> savedCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo, org.mockito.Mockito.atLeastOnce()).save(savedCaptor.capture());
        Payment saved = savedCaptor.getAllValues().get(savedCaptor.getAllValues().size() - 1);
        assertThat(saved.getGatewayOrderId()).isNull();
        assertThat(saved.getGatewayName()).isEqualTo("manual");
    }

    @Test
    @DisplayName("[+] payCash on OVERDUE row also succeeds (post-due-date settlement)")
    void cash_overdue_settlement_path() {
        stubReceiptInfra();
        Payment p = pending("P-OVERDUE-1");
        p.setStatus(PaymentStatus.OVERDUE);   // late settlement
        when(paymentRepo.findById(eq("P-OVERDUE-1"))).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse resp = service().payCash("P-OVERDUE-1",
                new PayCashRequest("O-1", "RCPT-555"));

        assertThat(resp.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(resp.paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    /* ───────────────────────── NEGATIVE ───────────────────────── */

    @Test
    @DisplayName("[-] payCash on already-PAID throws PaymentAlreadyPaidException, no events fire")
    void cash_on_paid_throws() {
        Payment p = pending("P-DONE");
        p.setStatus(PaymentStatus.PAID);
        when(paymentRepo.findById(eq("P-DONE"))).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().payCash("P-DONE", new PayCashRequest("O-1", null)))
                .isInstanceOf(PaymentAlreadyPaidException.class);

        verify(events, never()).sendPaymentCompleted(any());
        verify(audit, never()).publishSuccess(eq("payment.cash.recorded"),
                any(), any(), any(), any());
        verify(paymentRepo, never()).save(any());
    }

    @Test
    @DisplayName("[-] payCash on CANCELLED throws PaymentAlreadyPaidException")
    void cash_on_cancelled_throws() {
        Payment p = pending("P-CANCEL");
        p.setStatus(PaymentStatus.CANCELLED);
        when(paymentRepo.findById(eq("P-CANCEL"))).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().payCash("P-CANCEL", new PayCashRequest("O-1", null)))
                .isInstanceOf(PaymentAlreadyPaidException.class);
        verify(events, never()).sendPaymentCompleted(any());
        verify(audit, never()).publishSuccess(eq("payment.cash.recorded"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("[-] payCash on unknown id throws PaymentNotFoundException")
    void cash_on_unknown_id_throws() {
        when(paymentRepo.findById(eq("P-NONE"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().payCash("P-NONE", new PayCashRequest("O-1", null)))
                .isInstanceOf(PaymentNotFoundException.class);
        verify(events, never()).sendPaymentCompleted(any());
        verify(audit, never()).publishSuccess(eq("payment.cash.recorded"),
                any(), any(), any(), any());
    }
}
