package com.spa.home_rental_application.user_service.user_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "auth_user_id", nullable = false)
    private String authUserId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender")
    private String gender;

    @Column(name = "address")
    @Lob
    private String address;

    // Pre-signed download URLs from document-service. With expires +
    // HMAC signature query params they run ~150-250 chars on the local
    // backend and can grow well past Oracle's default VARCHAR2(255)
    // behind a proxy / ngrok / longer host. @Lob avoids the silent
    // overflow that surfaces as an "An unexpected error occurred"
    // INTERNAL_ERROR when JPA tries to save the row.
    @Lob
    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Lob
    @Column(name = "id_proof_url")
    private String idProofUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ----- India Compliance Layer (added by KYC + Document consumers) -----
    @Column(name = "kyc_status", length = 20)
    @Builder.Default
    private String kycStatus = "PENDING";          // PENDING | INITIATED | VERIFIED | FAILED

    @Column(name = "kyc_provider", length = 50)
    private String kycProvider;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "whatsapp_number", length = 15)
    private String whatsappNumber;

    @Override
    public String toString() {
        return "User{" +
                "userId='" + id + '\'' +
                ", authUserId='" + authUserId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", dateOfBirth=" + dateOfBirth +
                ", gender='" + gender + '\'' +
                ", address='" + address + '\'' +
                ", profilePictureUrl='" + profilePictureUrl + '\'' +
                ", idProofUrl='" + idProofUrl + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
