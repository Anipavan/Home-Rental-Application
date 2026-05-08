package com.spa.home_rental_application.review_service.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
        @NotBlank String reviewerId,
        @NotBlank String reviewerType,        // TENANT | OWNER
        @NotBlank String targetId,
        @NotBlank String targetType,          // PROPERTY | OWNER | TENANT
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 200) String title,
        @Size(max = 4000) String body,
        List<@Size(max = 50) String> tags
) {
}
