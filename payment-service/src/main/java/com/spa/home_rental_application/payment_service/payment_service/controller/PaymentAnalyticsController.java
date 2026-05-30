package com.spa.home_rental_application.payment_service.payment_service.controller;

import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentResponse;
import com.spa.home_rental_application.payment_service.payment_service.DTO.Response.PaymentStatsResponse;
import com.spa.home_rental_application.payment_service.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payment Analytics", description = "Tenant + owner aggregate payment statistics")
public class PaymentAnalyticsController {

    private final PaymentService paymentService;

    public PaymentAnalyticsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Aggregate payment stats for a tenant")
    @GetMapping("/stats/tenant/{tenantId}")
    public ResponseEntity<PaymentStatsResponse> tenantStats(@PathVariable String tenantId) {
        return ResponseEntity.ok(paymentService.getStatsByTenant(tenantId));
    }

    @Operation(summary = "Aggregate payment stats (revenue) for an owner")
    @GetMapping("/stats/owner/{ownerId}")
    public ResponseEntity<PaymentStatsResponse> ownerStats(@PathVariable String ownerId) {
        return ResponseEntity.ok(paymentService.getStatsByOwner(ownerId));
    }

    @Operation(summary = "Tenant payment history (alias for /payments/tenant/{id})")
    @GetMapping("/history/tenant/{tenantId}")
    public ResponseEntity<List<PaymentResponse>> history(@PathVariable String tenantId) {
        return ResponseEntity.ok(paymentService.getPaymentsByTenant(tenantId));
    }

    @Operation(summary = "Public lifetime collected rupees — for landing-page social proof")
    @GetMapping("/stats/public/lifetime")
    public ResponseEntity<Map<String, Object>> publicLifetimeStats() {
        // No auth: this is a single aggregate number, no PII. Whitelisted
        // in the api-gateway so the public landing page can fetch it
        // without a token.
        BigDecimal total = paymentService.getLifetimeCollectedRupees();
        return ResponseEntity.ok(Map.of(
                "totalCollectedRupees", total
        ));
    }
}
