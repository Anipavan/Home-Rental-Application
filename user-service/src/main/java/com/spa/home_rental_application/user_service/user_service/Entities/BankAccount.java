package com.spa.home_rental_application.user_service.user_service.Entities;

import com.spa.home_rental_application.user_service.user_service.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The signed-in user's saved bank account. One row per user — the
 * unique constraint on {@code user_id} guarantees the
 * "Add bank details" form behaves like an Amazon-style "primary
 * payout method" rather than a many-to-many list.
 *
 * <p>The full {@code accountNumber} is persisted so it can be used
 * downstream (e.g. owner-payouts), but the response DTO ALWAYS masks
 * everything except the last 4 digits so the SPA never exposes the
 * full number on screen. Mutations require the caller to be the
 * row's owner or an admin (gateway-stamped X-Auth-User-Id).
 */
@Entity
@Table(
        name = "bank_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bank_accounts_user", columnNames = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** auth-user id — same value stored on User.authUserId. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "account_holder_name", nullable = false, length = 120)
    private String accountHolderName;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    /**
     * Full account number — sensitive. Encrypted at rest via
     * {@link EncryptedStringConverter} (AES-256-GCM); the entity
     * field still exposes plaintext to service-layer code, but the
     * database stores {@code ENC:<base64(iv||ciphertext)>}.
     * <p>Service-layer mappers must still strip everything except
     * the last 4 digits before returning to the SPA — the
     * encryption protects the data at rest, not in transit through
     * our own services.
     * <p>Column length bumped from 30 → 200 to fit the
     * {@code ENC:} sentinel + base64 ciphertext overhead. Hibernate
     * {@code ddl-auto=update} widens the column on existing schemas
     * without data loss.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", nullable = false, length = 200)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 11)
    private String ifscCode;

    @Column(name = "branch", length = 200)
    private String branch;

    /** SAVINGS | CURRENT — left as a String to avoid an enum-table migration. */
    @Column(name = "account_type", length = 20)
    private String accountType;

    /** Optional UPI VPA (e.g. {@code user@oksbi}). */
    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
