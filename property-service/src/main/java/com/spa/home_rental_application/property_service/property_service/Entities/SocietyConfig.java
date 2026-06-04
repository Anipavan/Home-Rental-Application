package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One-time society / common-area maintenance configuration per
 * building. Created when an owner clicks "Set up society" on the
 * building detail page; one row per building (unique constraint
 * on {@code building_id}).
 *
 * <p>The {@code maintainer_user_id} is the user authorised to
 * record expenses. It's almost always the building owner (self-
 * assigned), but the schema supports nominating a third-party
 * maintainer with their own login — future register-time invite
 * flow flips a new user's role to MAINTAINER and links them here.
 *
 * <p>The {@code public_view_token} is the random URL-safe string in
 * the shareable read-only link
 * ({@code /society/view/{token}}). Anyone with the link sees the
 * read-only ledger without logging in. Rotatable when leaked.
 */
@Entity
@Table(
        name = "building_society_config",
        indexes = {
                @Index(name = "uq_society_building", columnList = "building_id", unique = true),
                @Index(name = "uq_society_token", columnList = "public_view_token", unique = true),
                @Index(name = "idx_society_maintainer", columnList = "maintainer_user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocietyConfig {

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 36, nullable = false, unique = true)
    private String buildingId;

    /** Day-of-month (1-28, safe across short months) the dues are
     *  considered due. Display-only today; the payment-overdue cron
     *  consumes this once payments wire. */
    @Column(name = "monthly_due_day", nullable = false)
    @Builder.Default
    private Integer monthlyDueDay = 5;

    /** Per-flat default in ₹. Overridable per-flat via
     *  {@link FlatMaintenanceDues}; absent override = use this. */
    @Column(name = "default_per_flat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultPerFlatAmount;

    /** authUserId of the person who manages this society's books.
     *  Owner-self-assign is the common case; an invited third-party
     *  maintainer (different user with MAINTAINER role) is also
     *  supported. */
    @Column(name = "maintainer_user_id", length = 64, nullable = false)
    private String maintainerUserId;

    /** Random ~32-char token. Anyone with the URL containing this
     *  token can view the read-only ledger via
     *  {@code /society/view/{token}}. Regenerate when leaked — old
     *  token is replaced atomically, no grace period. */
    @Column(name = "public_view_token", length = 64, nullable = false, unique = true)
    private String publicViewToken;

    /** Friendly label rendered on the ledger header. Defaults to the
     *  building name on first create; owners often rename to
     *  "&lt;Building&gt; Residents Welfare Fund" or similar. */
    @Column(name = "society_display_name", length = 200)
    private String societyDisplayName;

    /* ─── Collection bank / UPI fields (V5) ───
     * All nullable. Society sets these once via the owner setup
     * wizard or the maintainer's bank panel. The tenant Pay-Now
     * button on /app/society renders a UPI QR code from
     * {@code upi_id} + {@code payee_name} + the per-row amount.
     * {@code account_number} and {@code ifsc_code} are pure
     * informational — shown below the QR for tenants who prefer
     * to add the account as a banking beneficiary instead of
     * scanning.
     */

    /** UPI handle the QR resolves to (e.g. {@code anirudh@oksbi}).
     *  Required for the Pay-Now button to appear on the tenant page;
     *  societies that only collect via cash / NEFT leave this null. */
    @Column(name = "upi_id", length = 64)
    private String upiId;

    /** Name shown by the tenant's UPI app on the payment confirm
     *  screen. Typically the registered bank account holder name,
     *  which may differ from {@link #societyDisplayName} (the
     *  friendly label). */
    @Column(name = "payee_name", length = 200)
    private String payeeName;

    /** Optional bank account number — informational. */
    @Column(name = "account_number", length = 32)
    private String accountNumber;

    /** Optional IFSC code (11 chars per spec, column 16 for
     *  headroom). Informational. */
    @Column(name = "ifsc_code", length = 16)
    private String ifscCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
