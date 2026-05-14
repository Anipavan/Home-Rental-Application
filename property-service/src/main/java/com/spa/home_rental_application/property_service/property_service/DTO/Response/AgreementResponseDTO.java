package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lease agreement DTO returned to the SPA. */
public record AgreementResponseDTO(
        String id,
        String flatId,
        String buildingId,
        String tenantId,
        String ownerId,
        /**
         * Tenant's full name resolved via user-service KYC, so the SPA can
         * render "Owner: John Doe" instead of leaking the raw UUID into
         * the lease card (Issue #5). Null when user-service is unreachable
         * or the user record has no name on file; the UI falls back to the
         * raw id in that case.
         */
        String tenantName,
        /** Owner's full name — same treatment as tenantName. */
        String ownerName,
        BigDecimal rentAmount,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        String terms,
        String status,                    // PENDING_SIGNATURE | SIGNED | REJECTED
        String signatureData,             // base64 PNG, null until signed
        LocalDateTime signedAt,
        LocalDateTime rejectedAt,
        String rejectionReason,
        /**
         * Truthy when the rendered PDF exists on disk and can be downloaded
         * from {@code /properties/agreements/{id}/document}. Frontend uses
         * this to enable the "Download deed (PDF)" button.
         */
        Boolean hasDocument,
        /**
         * Truthy when the wet-signed, notary-stamped PDF has been uploaded
         * by the parties and can be downloaded from
         * {@code /properties/agreements/{id}/signed-deed}. Surfaces the
         * "Download notarized copy" button.
         */
        Boolean hasSignedDeed,
        /** When the notarized PDF was uploaded. Null until uploaded. */
        LocalDateTime notarizedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
