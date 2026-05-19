package com.spa.home_rental_application.user_service.user_service.DTO.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponseDto(
        String id,
        String authUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String profilePictureUrl,
        String idProofUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Optional. SINGLE | MARRIED | DIVORCED | WIDOWED. */
        String maritalStatus,
        /** Optional. BACHELOR | FAMILY (only meaningful for TENANT users). */
        String tenantType,
        /**
         * KYC verification status. PENDING (default) | INITIATED | VERIFIED |
         * FAILED. Surfaced on the wire so property-service can render a
         * "Verified owner" badge on public listings, and so the user can see
         * their own KYC progress on /app/profile. KYC service itself is
         * paused right now — every new account stays at PENDING — but the
         * code path is wired end-to-end so flipping KYC back on
         * automatically lights up the badge.
         */
        String kycStatus
) {}
