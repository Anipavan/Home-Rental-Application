package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
import com.spa.home_rental_application.payment_service.payment_service.client.UserClient;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.PayCashRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PayoutDetailsResponse;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant-pays-rent-direct-to-owner flow coverage.
 *
 *  POSITIVE
 *   - Owner with UPI VPA → response carries upiVpa + a fully-formed
 *     UPI deep-link payload that's safe to QR-encode on the FE.
 *   - Owner with only bank account (no UPI) → response carries the
 *     masked-account + IFSC fallback, upiVpa null + upiQrPayload null.
 *   - Mark-UPI-received transitions PENDING → PAID, sets
 *     method=UPI, gateway=manual-upi, and audits as
 *     payment.upi.received.
 *   - Mark-UPI-received without an explicit reference auto-generates
 *     a UPI-<uuid> transaction id.
 *
 *  NEGATIVE
 *   - Payment with no ownerId (legacy null-owner row before the
 *     back-fill runs) → ownerPayoutMissing=true, no exception.
 *   - Owner has not saved a bank account at all (Feign returns empty
 *     fields) → ownerPayoutMissing=true.
 *   - Feign blows up (user-service down) → fallback empty, response
 *     is ownerPayoutMissing=true, NOT a 500.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPayoutDetailsTest {

    @Mock PaymentRepository paymentRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock ReceiptRepository receiptRepo;
    @Mock ProcessedWebhookRepository webhookRepo;
    @Mock PaymentGateway gateway;
    @Mock PaymentServiceEvents events;
    @Mock PropertyClient propertyClient;
    @Mock UserClient userClient;
    @Mock AuditEventPublisher audit;

    private PaymentServiceImpl service() {
        return new PaymentServiceImpl(paymentRepo, invoiceRepo, receiptRepo, webhookRepo, gateway, events,
                new PaymentProperties(), new PaymentPdfGenerator(),
                propertyClient, userClient, audit);
    }

    private Payment pending(String paymentId, String ownerId) {
        return Payment.builder()
                .id(paymentId).tenantId("T-1").flatId("F-A-301").ownerId(ownerId)
                .amount(new BigDecimal("15000"))
                .lateFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("15000"))
                .dueDate(LocalDate.of(2026, 6, 1))
                .status(PaymentStatus.PENDING)
                .build();
    }

    /* ───────────────────────── POSITIVE ───────────────────────── */

    @Test
    @DisplayName("[+] owner with UPI VPA → response carries upiQrPayload deep link")
    void owner_with_upi_returns_qr_payload() {
        Payment p = pending("P-UPI-1", "O-1");
        when(paymentRepo.findById(eq("P-UPI-1"))).thenReturn(Optional.of(p));
        when(userClient.getPayoutDetails(eq("O-1"))).thenReturn(new UserClient.PayoutDetails(
                "Jane Owner", "State Bank of India", "XXXX XXXX 9012",
                "SBIN0001234", "MG Road", "SAVINGS", "jane@oksbi"));

        PayoutDetailsResponse resp = service().getPayoutDetails("P-UPI-1");

        assertThat(resp.ownerPayoutMissing()).isFalse();
        assertThat(resp.upiVpa()).isEqualTo("jane@oksbi");
        assertThat(resp.upiQrPayload()).startsWith("upi://pay?pa=jane");
        // Amount is URL-encoded into the deep link with 2dp.
        assertThat(resp.upiQrPayload()).contains("am=15000.00");
        // Payee name is URL-encoded (space becomes +).
        assertThat(resp.upiQrPayload()).contains("pn=Jane");
        assertThat(resp.upiQrPayload()).contains("cu=INR");
        // Bank fallback is still present for tenants who'd rather
        // use NEFT/IMPS.
        assertThat(resp.accountNumberMasked()).isEqualTo("XXXX XXXX 9012");
        assertThat(resp.ifscCode()).isEqualTo("SBIN0001234");
    }

    @Test
    @DisplayName("[+] owner with only bank account (no UPI) → null payload, bank fallback present")
    void owner_without_upi_returns_bank_fallback_only() {
        Payment p = pending("P-BANK-1", "O-2");
        when(paymentRepo.findById(eq("P-BANK-1"))).thenReturn(Optional.of(p));
        when(userClient.getPayoutDetails(eq("O-2"))).thenReturn(new UserClient.PayoutDetails(
                "Mike Owner", "HDFC Bank", "XXXX XXXX 5555",
                "HDFC0009999", null, "CURRENT", null));     // ← no upiId

        PayoutDetailsResponse resp = service().getPayoutDetails("P-BANK-1");

        assertThat(resp.ownerPayoutMissing()).isFalse();
        assertThat(resp.upiVpa()).isNull();
        assertThat(resp.upiQrPayload()).isNull();
        assertThat(resp.accountNumberMasked()).isEqualTo("XXXX XXXX 5555");
        assertThat(resp.ifscCode()).isEqualTo("HDFC0009999");
    }

    @Test
    @DisplayName("[+] mark UPI received: PENDING → PAID, method=UPI, audited as payment.upi.received")
    void mark_upi_received_marks_paid_and_audits() {
        stubReceiptInfra();
        Payment p = pending("P-UPI-R-1", "O-1");
        when(paymentRepo.findById(eq("P-UPI-R-1"))).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service().markUpiReceived("P-UPI-R-1",
                new PayCashRequest("O-1", "UPI/412395123456/SBI"));

        assertThat(resp.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(resp.paymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(resp.gatewayName()).isEqualTo("manual-upi");
        assertThat(resp.transactionId()).isEqualTo("UPI/412395123456/SBI");

        verify(audit).publishSuccess(
                eq("payment.upi.received"),
                eq("O-1"),       // actor = owner
                eq("T-1"),       // subject = tenant
                eq("P-UPI-R-1"),
                any());
    }

    @Test
    @DisplayName("[+] mark UPI received without reference auto-generates UPI-<uuid> txn id")
    void mark_upi_received_without_reference_auto_generates_txn() {
        stubReceiptInfra();
        Payment p = pending("P-UPI-R-2", "O-1");
        when(paymentRepo.findById(eq("P-UPI-R-2"))).thenReturn(Optional.of(p));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service().markUpiReceived("P-UPI-R-2",
                new PayCashRequest("O-1", null));

        assertThat(resp.transactionId()).startsWith("UPI-");
        assertThat(resp.transactionId().length()).isGreaterThan(5);
    }

    /* ───────────────────────── NEGATIVE ───────────────────────── */

    @Test
    @DisplayName("[-] payment with no ownerId → ownerPayoutMissing=true (no Feign call attempted)")
    void payment_without_owner_returns_missing() {
        Payment p = pending("P-NO-OWNER", null);    // ← legacy null-owner row
        when(paymentRepo.findById(eq("P-NO-OWNER"))).thenReturn(Optional.of(p));

        PayoutDetailsResponse resp = service().getPayoutDetails("P-NO-OWNER");

        assertThat(resp.ownerPayoutMissing()).isTrue();
        assertThat(resp.upiVpa()).isNull();
        assertThat(resp.upiQrPayload()).isNull();
        // We never attempted the Feign call — short-circuited on null ownerId.
        verify(userClient, never()).getPayoutDetails(any());
    }

    @Test
    @DisplayName("[-] owner has no bank account on file → ownerPayoutMissing=true")
    void owner_without_bank_account_returns_missing() {
        Payment p = pending("P-MISSING-1", "O-NO-BANK");
        when(paymentRepo.findById(eq("P-MISSING-1"))).thenReturn(Optional.of(p));
        // Feign returned all-null fields = no account on file.
        when(userClient.getPayoutDetails(eq("O-NO-BANK")))
                .thenReturn(UserClient.PayoutDetails.empty());

        PayoutDetailsResponse resp = service().getPayoutDetails("P-MISSING-1");

        assertThat(resp.ownerPayoutMissing()).isTrue();
    }

    @Test
    @DisplayName("[-] user-service throws → ownerPayoutMissing=true, no exception bubbles")
    void user_service_outage_returns_missing_not_500() {
        Payment p = pending("P-OUTAGE", "O-3");
        when(paymentRepo.findById(eq("P-OUTAGE"))).thenReturn(Optional.of(p));
        when(userClient.getPayoutDetails(eq("O-3")))
                .thenThrow(new RuntimeException("user-service unreachable"));

        PayoutDetailsResponse resp = service().getPayoutDetails("P-OUTAGE");

        assertThat(resp.ownerPayoutMissing()).isTrue();
        assertThat(resp.upiVpa()).isNull();
    }

    /* ──────────────────────── helpers ──────────────────────── */

    private void stubReceiptInfra() {
        lenient().when(receiptRepo.save(any(com.spa.home_rental_application.payment_service.payment_service.entities.Receipt.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }
}
