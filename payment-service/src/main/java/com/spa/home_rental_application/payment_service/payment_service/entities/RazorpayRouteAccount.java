package com.spa.home_rental_application.payment_service.payment_service.entities;

import com.spa.home_rental_application.payment_service.payment_service.enums.BusinessType;
import com.spa.home_rental_application.payment_service.payment_service.enums.RouteAccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row per owner who has (or is applying to) receive split
 * payments via Razorpay Route.
 *
 * <p>Owned by payment-service. Not linked to user-service's
 * {@code BANK_ACCOUNTS} table via JPA relationship — we key by
 * {@code user_id} instead so the two services can evolve independently.
 *
 * <p>Sensitive data (raw PAN, full bank account #) is NOT stored here.
 * We send them to Razorpay to create the Linked Account, keep only
 * the last-four for display, and rely on Razorpay as the system of
 * record for the full values. Rationale: reduces the blast radius if
 * this table is ever exfiltrated + keeps us out of DPDP-Act
 * regulated-PII storage territory.
 */
@Entity
@Table(name = "razorpay_route_accounts", indexes = {
        @Index(name = "uq_route_acc_user", columnList = "user_id", unique = true),
        @Index(name = "idx_route_acc_status", columnList = "status"),
        @Index(name = "idx_route_acc_rzp_id", columnList = "razorpay_account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayRouteAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Auth-service user id (string form). Unique — one owner has at
     *  most one Route Linked Account across the platform's lifetime.
     *  If an owner is rejected, we UPDATE this row rather than insert
     *  a second. */
    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    /** Razorpay's opaque id for the account, e.g. {@code acc_ABC123XYZ}.
     *  Nullable until Razorpay accepts the create request (which
     *  usually succeeds synchronously, but the schema tolerates a
     *  transient failure between form-submit and API-callback). */
    @Column(name = "razorpay_account_id", length = 64)
    private String razorpayAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RouteAccountStatus status = RouteAccountStatus.NOT_STARTED;

    /* ─── Business identity (sent to Razorpay on create) ─────── */

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 30)
    private BusinessType businessType;

    /** Legal name as per PAN / incorporation certificate. */
    @Column(name = "legal_business_name", length = 200)
    private String legalBusinessName;

    /** Display name — what the tenant sees on their payment receipt.
     *  Owners frequently want this to be a friendly form
     *  ("Anirudh's Homes") rather than the legal "Anirudh Sivapavan". */
    @Column(name = "business_name", length = 200)
    private String businessName;

    /** Owner's real name (as on PAN). */
    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /** E.164 format, e.g. {@code +919876543210}. */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /* ─── Identifiers stored only as last-four for display ────── */

    /** Last 4 digits of the PAN. Full PAN is sent to Razorpay + never
     *  persisted locally. */
    @Column(name = "pan_last4", length = 4)
    private String panLast4;

    /** Optional — only applies to non-INDIVIDUAL business types. */
    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    /* ─── Bank account (last-four only) ────────────────────────── */

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    /** Sometimes differs from contact_name (e.g. joint accounts). */
    @Column(name = "bank_account_holder_name", length = 200)
    private String bankAccountHolderName;

    /* ─── Registered address (Razorpay requires) ───────────────── */

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_postal_code", length = 10)
    private String addressPostalCode;

    /** ISO 3166-1 alpha-2 country code. Defaults to "IN" — every
     *  owner we onboard is in India, but keeping the column open
     *  avoids a migration if we ever expand. */
    @Column(name = "address_country", length = 2)
    @Builder.Default
    private String addressCountry = "IN";

    /* ─── Lifecycle timestamps + reason strings ────────────────── */

    /** When the owner first submitted the form (transition
     *  NOT_STARTED → SUBMITTED). */
    @Column(name = "kyc_submitted_at")
    private Instant kycSubmittedAt;

    /** When Razorpay flipped us to ACTIVATED (nullable — most rows
     *  won't have this). */
    @Column(name = "activated_at")
    private Instant activatedAt;

    /** Free-text reason Razorpay gave when REJECTED / NEEDS_CLARIFICATION /
     *  SUSPENDED. Populated from the {@code account.updated} webhook
     *  payload. */
    @Column(name = "status_reason", length = 2000)
    private String statusReason;

    /** When we last received a status update from Razorpay
     *  (webhook OR poll). Helps debug "why hasn't this activated yet". */
    @Column(name = "last_status_updated_at")
    private Instant lastStatusUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = RouteAccountStatus.NOT_STARTED;
        if (addressCountry == null) addressCountry = "IN";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
