package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.config.PaymentProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import com.spa.home_rental_application.payment_service.payment_service.gateway.PaymentGateway;
import com.spa.home_rental_application.payment_service.payment_service.repository.InvoiceRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.PaymentRepository;
import com.spa.home_rental_application.payment_service.payment_service.repository.ReceiptRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLateFeeAndAnalyticsTest {

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
    void computeLateFee_zeroDays_returnsZero() {
        BigDecimal fee = service().computeLateFee(new BigDecimal("8500"), 0);
        assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeLateFee_oneWeek_appliesTwoPercent() {
        // Default props: 2% per week, capped at 10%
        BigDecimal fee = service().computeLateFee(new BigDecimal("10000"), 7);
        assertThat(fee).isEqualByComparingTo("200.00");
    }

    @Test
    void computeLateFee_eightWeeks_capsAtMax() {
        // 8 weeks * 2% = 16% which exceeds 10% cap → 10% applied
        BigDecimal fee = service().computeLateFee(new BigDecimal("10000"), 56);
        assertThat(fee).isEqualByComparingTo("1000.00");
    }

    @Test
    void computeLateFee_twoDaysOverdue_appliesOneWeek() {
        // 2 days rounds up to 1 week (ceiling) → 2% of 10000 = 200
        BigDecimal fee = service().computeLateFee(new BigDecimal("10000"), 2);
        assertThat(fee).isEqualByComparingTo("200.00");
    }

    @Test
    void getStatsByOwner_aggregatesPaidPendingOverdueFailedAndLateFees() {
        Payment paid = Payment.builder().id("P1").ownerId("O1").status(PaymentStatus.PAID)
                .amount(new BigDecimal("8500")).totalAmount(new BigDecimal("8500"))
                .lateFee(new BigDecimal("100")).build();
        Payment pending = Payment.builder().id("P2").ownerId("O1").status(PaymentStatus.PENDING)
                .amount(new BigDecimal("8500")).totalAmount(new BigDecimal("8500"))
                .lateFee(BigDecimal.ZERO).build();
        Payment overdue = Payment.builder().id("P3").ownerId("O1").status(PaymentStatus.OVERDUE)
                .amount(new BigDecimal("8500")).totalAmount(new BigDecimal("9000"))
                .lateFee(new BigDecimal("500")).build();
        Payment failed = Payment.builder().id("P4").ownerId("O1").status(PaymentStatus.FAILED)
                .amount(new BigDecimal("8500")).totalAmount(new BigDecimal("8500"))
                .lateFee(BigDecimal.ZERO).build();

        when(paymentRepo.findByOwnerId("O1")).thenReturn(List.of(paid, pending, overdue, failed));

        var stats = service().getStatsByOwner("O1");
        assertThat(stats.totalPayments()).isEqualTo(4);
        assertThat(stats.paidCount()).isEqualTo(1);
        assertThat(stats.pendingCount()).isEqualTo(1);
        assertThat(stats.overdueCount()).isEqualTo(1);
        assertThat(stats.failedCount()).isEqualTo(1);
        assertThat(stats.totalAmountPaid()).isEqualByComparingTo("8500");
        assertThat(stats.totalAmountPending()).isEqualByComparingTo("17500"); // pending + overdue total
        assertThat(stats.totalLateFeeCollected()).isEqualByComparingTo("600");
    }
}
