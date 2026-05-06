package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.InitiatePaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.UpiApp;
import com.spa.home_rental_application.payment_service.payment_service.enums.WalletProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gw = new MockPaymentGateway();

    private Payment payment(BigDecimal amount) {
        return Payment.builder().id("PAY-1").totalAmount(amount).build();
    }

    @Test
    void upi_returnsValidUpiIntentUrl() {
        var req = new InitiatePaymentRequest("PAY-1", PaymentMethod.UPI, UpiApp.GPAY,
                "siva@oksbi", null, null, null, null, null);
        var r = gw.initiate(payment(new BigDecimal("8500")), req);
        assertThat(r.getGatewayName()).isEqualTo("mock");
        assertThat(r.getUpiIntentUrl()).startsWith("upi://pay?pa=siva@oksbi");
        assertThat(r.getUpiIntentUrl()).contains("am=8500");
        assertThat(r.getUpiCollectStatus()).isEqualTo("PENDING_USER_ACTION");
    }

    @Test
    void card_returnsRedirectUrl() {
        var req = new InitiatePaymentRequest("PAY-1", PaymentMethod.CARD, null, null,
                null, null, null, null, null);
        var r = gw.initiate(payment(new BigDecimal("8500")), req);
        assertThat(r.getRedirectUrl()).contains("/payments/mock/return");
        assertThat(r.getRedirectUrl()).contains("paymentId=PAY-1");
    }

    @Test
    void wallet_returnsRedirectUrl() {
        var req = new InitiatePaymentRequest("PAY-1", PaymentMethod.WALLET, null, null,
                WalletProvider.PAYTM, null, null, null, null);
        var r = gw.initiate(payment(new BigDecimal("8500")), req);
        assertThat(r.getRedirectUrl()).contains("/payments/mock/return");
    }

    @Test
    void bankTransfer_returnsBankDetails() {
        var req = new InitiatePaymentRequest("PAY-1", PaymentMethod.BANK_TRANSFER, null,
                null, null, null, null, null, null);
        var r = gw.initiate(payment(new BigDecimal("8500")), req);
        assertThat(r.getBankAccountNumber()).isNotBlank();
        assertThat(r.getBankIfsc()).isNotBlank();
    }

    @Test
    void verify_acceptsMockOk() {
        var req = new VerifyPaymentRequest("PAY-1", "order_x", "MOCK_OK", "sig");
        var r = gw.verify(payment(BigDecimal.TEN), req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTransactionId()).isEqualTo("MOCK_OK");
    }

    @Test
    void verify_rejectsRandomTxnId() {
        var req = new VerifyPaymentRequest("PAY-1", "order_x", "garbage", "sig");
        var r = gw.verify(payment(BigDecimal.TEN), req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getGatewayErrorCode()).isEqualTo("MOCK_VERIFICATION_FAILED");
    }
}
