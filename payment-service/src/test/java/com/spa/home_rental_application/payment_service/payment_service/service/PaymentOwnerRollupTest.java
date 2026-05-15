package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-15c: owner outstanding-rollup + lazy back-fill coverage for
 * PaymentServiceImpl.getPaymentsByOwner.
 *
 *  POSITIVE
 *   - Rows already tagged with ownerId return immediately from pass 1.
 *   - Legacy rows (ownerId=null) whose flat belongs to the owner are
 *     found in pass 2 via the Property Feign client.
 *   - Pass 2 also back-fills the ownerId column on every legacy row
 *     it finds, then saveAll persists them so the next call serves
 *     them from pass 1.
 *   - Duplicate rows (same id surfaced by both passes) are merged
 *     and returned once.
 *
 *  NEGATIVE
 *   - Unknown owner with no flats → empty list, no Feign hits beyond
 *     the building lookup, no saves attempted.
 *   - Feign property-client failure (collectOwnerFlatIds returns
 *     empty) doesn't blow up — pass 1 still returns its rows.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOwnerRollupTest {

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

    private static Payment payment(String id, String tenantId, String flatId,
                                    String ownerId, PaymentStatus status,
                                    BigDecimal amount) {
        Payment p = Payment.builder()
                .id(id).tenantId(tenantId).flatId(flatId).ownerId(ownerId)
                .amount(amount).lateFee(BigDecimal.ZERO).totalAmount(amount)
                .dueDate(LocalDate.of(2026, 6, 1))
                .status(status)
                .build();
        return p;
    }

    /* ───────────────────────── POSITIVE ───────────────────────── */

    @Test
    @DisplayName("[+] pass 1: rows already tagged with ownerId surface immediately")
    void pass_one_returns_tagged_rows() {
        Payment tagged = payment("P-1", "T-1", "F-1", "O-1", PaymentStatus.PENDING, new BigDecimal("10000"));
        when(paymentRepo.findByOwnerId(eq("O-1"))).thenReturn(List.of(tagged));
        // No buildings on file → pass 2 finds nothing
        when(propertyClient.getBuildingsByOwner(eq("O-1"))).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = service().getPaymentsByOwner("O-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("P-1");
        // No back-fill saves needed — the row is already tagged.
        verify(paymentRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("[+] pass 2: legacy ownerId=null rows discovered via Feign + back-filled")
    void pass_two_discovers_legacy_rows_and_backfills() {
        Payment legacy = payment("P-LEGACY", "T-1", "F-2", null, PaymentStatus.OVERDUE,
                new BigDecimal("15000"));
        when(paymentRepo.findByOwnerId(eq("O-2"))).thenReturn(Collections.emptyList());
        // Property-service says O-2 owns building B-2 which has flat F-2
        when(propertyClient.getBuildingsByOwner(eq("O-2"))).thenReturn(List.of(
                new PropertyClient.BuildingSummary("B-2", "O-2")));
        when(propertyClient.getFlatsByBuilding(eq("B-2"))).thenReturn(List.of(
                new PropertyClient.FlatSummary("F-2", "B-2", "T-1")));
        when(paymentRepo.findByFlatIdIn(anySet())).thenReturn(List.of(legacy));

        List<PaymentResponse> result = service().getPaymentsByOwner("O-2");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("P-LEGACY");

        // Critical: the legacy row got its ownerId column populated +
        // saveAll was called so the heal is persisted.
        ArgumentCaptor<Iterable<Payment>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(paymentRepo).saveAll(savedCaptor.capture());
        Payment saved = savedCaptor.getValue().iterator().next();
        assertThat(saved.getId()).isEqualTo("P-LEGACY");
        assertThat(saved.getOwnerId()).isEqualTo("O-2");
    }

    @Test
    @DisplayName("[+] mixed: pass 1 tagged + pass 2 legacy = merged result, only legacy back-filled")
    void mixed_pass_one_and_pass_two() {
        Payment tagged = payment("P-A", "T-1", "F-A", "O-3", PaymentStatus.PAID, new BigDecimal("5000"));
        Payment legacy = payment("P-B", "T-2", "F-B", null, PaymentStatus.PENDING, new BigDecimal("8000"));
        when(paymentRepo.findByOwnerId(eq("O-3"))).thenReturn(List.of(tagged));
        when(propertyClient.getBuildingsByOwner(eq("O-3"))).thenReturn(List.of(
                new PropertyClient.BuildingSummary("B-3", "O-3")));
        when(propertyClient.getFlatsByBuilding(eq("B-3"))).thenReturn(List.of(
                new PropertyClient.FlatSummary("F-A", "B-3", "T-1"),
                new PropertyClient.FlatSummary("F-B", "B-3", "T-2")));
        // Pass 2 returns BOTH rows (already-tagged + legacy) because
        // findByFlatIdIn doesn't filter on ownerId.
        when(paymentRepo.findByFlatIdIn(anySet())).thenReturn(List.of(tagged, legacy));

        List<PaymentResponse> result = service().getPaymentsByOwner("O-3");

        // Merge dedups by id, so we get 2 distinct payments back.
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PaymentResponse::id)
                .containsExactlyInAnyOrder("P-A", "P-B");

        // Only the LEGACY row goes into the saveAll back-fill batch.
        ArgumentCaptor<Iterable<Payment>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(paymentRepo).saveAll(savedCaptor.capture());
        List<Payment> toHeal = new java.util.ArrayList<>();
        savedCaptor.getValue().forEach(toHeal::add);
        assertThat(toHeal).hasSize(1);
        assertThat(toHeal.get(0).getId()).isEqualTo("P-B");
        assertThat(toHeal.get(0).getOwnerId()).isEqualTo("O-3");
    }

    /* ───────────────────────── NEGATIVE ───────────────────────── */

    @Test
    @DisplayName("[-] owner with no rows + no flats returns empty list")
    void empty_owner_returns_empty() {
        when(paymentRepo.findByOwnerId(eq("O-EMPTY"))).thenReturn(Collections.emptyList());
        when(propertyClient.getBuildingsByOwner(eq("O-EMPTY"))).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = service().getPaymentsByOwner("O-EMPTY");

        assertThat(result).isEmpty();
        verify(paymentRepo, never()).saveAll(any());
        verify(paymentRepo, never()).findByFlatIdIn(anySet());
    }

    @Test
    @DisplayName("[-] property-service down (Feign returns empty) → pass 1 results still surface")
    void feign_failure_doesnt_break_pass_one() {
        Payment tagged = payment("P-OK", "T-1", "F-X", "O-4", PaymentStatus.PENDING, new BigDecimal("12000"));
        when(paymentRepo.findByOwnerId(eq("O-4"))).thenReturn(List.of(tagged));
        // Simulate Feign fallback returning empty list (or the catch
        // path in collectOwnerFlatIds returning Set.of()).
        when(propertyClient.getBuildingsByOwner(eq("O-4"))).thenReturn(Collections.emptyList());

        List<PaymentResponse> result = service().getPaymentsByOwner("O-4");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("P-OK");
        // Pass 2 skipped → no findByFlatIdIn → no saveAll
        verify(paymentRepo, never()).findByFlatIdIn(anySet());
        verify(paymentRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("[-] property-service throws → pass 1 still returns, no exception bubbles")
    void feign_throws_isolated_to_pass_two() {
        Payment tagged = payment("P-OK", "T-1", "F-X", "O-5", PaymentStatus.PENDING, new BigDecimal("12000"));
        when(paymentRepo.findByOwnerId(eq("O-5"))).thenReturn(List.of(tagged));
        when(propertyClient.getBuildingsByOwner(eq("O-5")))
                .thenThrow(new RuntimeException("property-service down"));

        List<PaymentResponse> result = service().getPaymentsByOwner("O-5");

        // The catch in collectOwnerFlatIds absorbs the exception and
        // returns Set.of() — pass 1 results still surface, no 500.
        assertThat(result).hasSize(1);
        verify(paymentRepo, never()).saveAll(any());
    }
}
