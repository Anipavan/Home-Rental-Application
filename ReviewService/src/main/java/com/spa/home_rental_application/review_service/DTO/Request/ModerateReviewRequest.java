package com.spa.home_rental_application.review_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record ModerateReviewRequest(
        @NotBlank String decision,        // APPROVED | REJECTED | FLAGGED
        @NotBlank String moderatorId,
        String reason
) {
}
