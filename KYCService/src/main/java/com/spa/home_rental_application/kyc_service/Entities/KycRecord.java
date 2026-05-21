package com.spa.home_rental_application.kyc_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistent KYC record for a user. One row per userId (UNIQUE constraint).
 * <p>
 * Aadhaar is stored as a SHA-256 hash (with a per-environment salt) — we never
 * persist the plain 12-digit number, in line with the DPDP Act 2023.
 */
@Entity
@Table(name = "kyc_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_kyc_user", columnNames = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "kyc_provider", length = 50)
    private String kycProvider;            // DIGIO | SIGNZY | MANUAL | MOCK

    @Column(name = "aadhaar_number_hash", length = 255)
    private String aadhaarNumberHash;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "pan_holder_name", length = 200)
    private String panHolderName;

    @Column(name = "verification_status", length = 20, nullable = false)
    @Builder.Default
    private String verificationStatus = "PENDING";   // PENDING | INITIATED | VERIFIED | FAILED

    @Column(name = "aadhaar_verified", nullable = false)
    @Builder.Default
    private Boolean aadhaarVerified = false;

    @Column(name = "pan_verified", nullable = false)
    @Builder.Default
    private Boolean panVerified = false;

    @Column(name = "face_match_score", precision = 5, scale = 4)
    private BigDecimal faceMatchScore;

    @Column(name = "digilocker_linked", nullable = false)
    @Builder.Default
    private Boolean digilockerLinked = false;

    @Column(name = "consent_recorded", nullable = false)
    @Builder.Default
    private Boolean consentRecorded = false;

    @Column(name = "kyc_reference_id", length = 100)
    private String kycReferenceId;        // Provider's correlation id

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // ---------- DigiLocker-specific fields ----------
    // The OAuth state token we minted on /authorize. Stored alongside its
    // expiry so the callback can validate both that the state matches and
    // that it hasn't aged past the configured TTL (RFC 6749 §10.12).
    @Column(name = "digilocker_state", length = 100)
    private String digilockerState;

    @Column(name = "digilocker_state_expires_at")
    private LocalDateTime digilockerStateExpiresAt;

    // Safe-to-display fragments pulled from the eAadhaar XML. The full
    // 12-digit Aadhaar is NEVER persisted — we hash + discard it the moment
    // we parse the XML. last4 + name + dob are what UPI / banks already
    // show for cross-checks, and are explicitly allowed by UIDAI's
    // "share masked Aadhaar" guidelines.
    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    @Column(name = "date_of_birth", length = 30)
    private String dateOfBirth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
