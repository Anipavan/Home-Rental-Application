package com.spa.home_rental_application.payment_service.payment_service.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Mirror of the {@code VendorApiCall} entity in kyc-service. Both
 * services write to the same {@code vendor_api_calls} table — the
 * admin Vendor Usage dashboard reads it from kyc-service, the
 * RazorpayPaymentGateway / future VPA / OCR calls write to it from
 * payment-service and document-service. Keep the column shape in
 * lockstep with kyc-service's copy; the table is created by Flyway
 * migration V3 in kyc-service.
 *
 * <p>This is intentionally a duplicate (not extracted into a shared
 * module) so each service can be deployed independently without
 * coordinating a commons-jar release. The trade-off is a small piece
 * of repeated code — accepted because the schema is stable.
 *
 * <p>Status values are identical to kyc-service's enum:
 * <ul>
 *   <li>{@code SUCCESS} — 2xx, usable result.</li>
 *   <li>{@code USER_ERROR} — 4xx from user input (bad VPA, invalid amount, etc.).</li>
 *   <li>{@code BILLING_ALERT} — vendor rejected because OUR account
 *       is out of balance / suspended / over quota. Triggers the
 *       admin escalation dialog.</li>
 *   <li>{@code OUTAGE} — 5xx / timeout / connection failure.</li>
 *   <li>{@code UNAUTHORIZED} — 401 even after key reload. Real auth bug.</li>
 * </ul>
 */
@Entity
@Table(
        name = "vendor_api_calls",
        indexes = {
                @Index(name = "idx_vendor_occurred",
                        columnList = "vendor_name, occurred_at DESC"),
                @Index(name = "idx_vendor_status",
                        columnList = "vendor_name, status, occurred_at DESC"),
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorApiCall {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    /**
     * Logical vendor + product identifier. For payment-service this
     * is one of: {@code RAZORPAY_ORDER_CREATE}, {@code RAZORPAY_VPA_VALIDATE}.
     * Format intentionally matches kyc-service's
     * {@code SANDBOX_NSDL_PAN} so the dashboard groups them visually.
     */
    @Column(name = "vendor_name", nullable = false, length = 64)
    private String vendorName;

    @Column(name = "vendor_endpoint", length = 256)
    private String vendorEndpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    /** Vendor's error message verbatim. Truncated to 1024 chars. */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /**
     * AuthUserId of the user whose action triggered this call. Lets
     * the admin dashboard answer "which users got hit at 2pm
     * yesterday". Null for system jobs (rare for Razorpay; reserved
     * for future scheduled-payment retries).
     */
    @Column(name = "triggered_by_user_id", length = 64)
    private String triggeredByUserId;

    public enum Status {
        SUCCESS,
        USER_ERROR,
        BILLING_ALERT,
        OUTAGE,
        UNAUTHORIZED
    }
}
