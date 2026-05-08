package com.spa.home_rental_application.review_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponseDto(
        String id,
        String reviewerId,
        String reviewerType,
        String targetId,
        String targetType,
        Integer rating,
        String title,
        String body,
        List<String> tags,
        Boolean isVerified,
        Boolean isModerated,
        String moderationStatus,
        String moderationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
