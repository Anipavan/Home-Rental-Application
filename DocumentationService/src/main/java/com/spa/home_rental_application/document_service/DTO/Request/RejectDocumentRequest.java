package com.spa.home_rental_application.document_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /documents/{id}/reject} (Issue #9). The owner
 * must provide a reason so the tenant knows what to fix before re-
 * uploading. Length is capped to fit the {@code rejection_reason}
 * column on {@code Document} (VARCHAR2(500)).
 */
public record RejectDocumentRequest(
        @NotBlank(message = "rejection reason is required")
        @Size(max = 500, message = "reason must be 500 characters or fewer")
        String reason
) {}
