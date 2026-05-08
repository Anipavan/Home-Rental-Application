package com.spa.home_rental_application.review_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Aggregate rating + histogram for a target (property / owner / tenant). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RatingSummaryDto(
        String targetId,
        String targetType,
        long totalReviews,
        double averageRating,
        Map<Integer, Long> ratingHistogram   // {5: 12, 4: 7, 3: 1, 2: 0, 1: 0}
) {
}
