package com.spa.home_rental_application.kyc_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * One row per outbound call to any third-party vendor (Sandbox NSDL,
 * Sandbox OCR, Razorpay, Resend, etc.). Drives the admin "Vendor usage"
 * dashboard and is the source of truth for billing-alert escalations.
 *
 * <p>Lives in kyc-service today because that's where the first billing
 * issue surfaced; the schema is intentionally vendor-agnostic so other
 * services (payment-service for Razorpay, notification-service for
 * Resend) can populate the same table when we choose to consolidate.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code SUCCESS} — the call returned 2xx and the result was usable.</li>
 *   <li>{@code USER_ERROR} — 422 / 400 caused by user-input mismatch
 *       (wrong DOB, wrong PAN format). Tracked separately from outages
 *       so an aggregate doesn't lump "we're broken" together with
 *       "the user typed wrong".</li>
 *   <li>{@code BILLING_ALERT} — vendor rejected because OUR account is
 *       out of credits / suspended / over quota. Operationally urgent.</li>
 *   <li>{@code OUTAGE} — 5xx / timeout / connect failure. Means the
 *       vendor is down (not us).</li>
 *   <li>{@code UNAUTHORIZED} — 401 even after a JWT refresh. Real auth
 *       bug (rotated key not updated, suspended account).</li>
 * </ul>
 *
 * <p>Indexed on (vendor_name, occurred_at desc) because every admin
 * dashboard query filters by vendor and orders by recency.
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
     * Logical vendor + product identifier. Examples:
     * {@code SANDBOX_NSDL_PAN}, {@code SANDBOX_OCR}, {@code RAZORPAY_ORDER},
     * {@code RESEND_EMAIL}. Short enough to fit a 64-char dashboard chip,
     * descriptive enough that an operator can act on it without code spelunking.
     */
    @Column(name = "vendor_name", nullable = false, length = 64)
    private String vendorName;

    /** Vendor's endpoint path — used in admin tooltips, not aggregated on. */
    @Column(name = "vendor_endpoint", length = 256)
    private String vendorEndpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    /**
     * HTTP status code or vendor-specific code. Null for transport
     * failures (no response received). Examples: "200", "422", "429".
     */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    /** Vendor's error message verbatim. Truncated to 1024 chars. */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /** Wall-clock time the call completed (or failed). UTC. */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Round-trip latency in milliseconds. Null for failures captured
     * before the request returned (timeout, DNS error, breaker open).
     */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /**
     * AuthUserId of the user whose action triggered this call. Lets
     * the admin dashboard answer "which users got hit when we ran
     * out of credits at 2pm yesterday". Null when the call originates
     * from a system job (rare for KYC; reserved for future channels).
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
