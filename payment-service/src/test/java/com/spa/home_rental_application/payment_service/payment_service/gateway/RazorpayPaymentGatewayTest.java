package com.spa.home_rental_application.payment_service.payment_service.gateway;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Request.VerifyPaymentRequest;
import com.spa.home_rental_application.payment_service.payment_service.config.RazorpayProperties;
import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpayPaymentGatewayTest {

    private RazorpayPaymentGateway buildGw() {
        RazorpayProperties p = new RazorpayProperties();
        p.setKeyId("rzp_test_key");
        p.setKeySecret("rzp_test_secret");
        p.setWebhookSecret("hooky");
        return new RazorpayPaymentGateway(p);
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verify_validHmac_succeeds() throws Exception {
        String orderId = "order_1";
        String txn = "pay_1";
        String sig = hmac(orderId + "|" + txn, "rzp_test_secret");
        var r = buildGw().verify(Payment.builder().build(),
                new VerifyPaymentRequest("PAY-1", orderId, txn, sig));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTransactionId()).isEqualTo("pay_1");
    }

    @Test
    void verify_wrongHmac_fails() {
        var r = buildGw().verify(Payment.builder().build(),
                new VerifyPaymentRequest("PAY-1", "order_1", "pay_1", "definitely-wrong"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getGatewayErrorCode()).isEqualTo("RAZORPAY_SIGNATURE_MISMATCH");
    }

    @Test
    void webhook_validHmac_succeeds() throws Exception {
        String body = "{\"some\":\"payload\"}";
        String sig = hmac(body, "hooky");
        var r = buildGw().verifyWebhook(body, sig);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void webhook_missingSig_fails() {
        var r = buildGw().verifyWebhook("{}", null);
        assertThat(r.valid()).isFalse();
    }
}
