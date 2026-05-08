package com.spa.home_rental_application.review_service.mapper;

import com.spa.home_rental_application.review_service.DTO.Response.ReviewResponseDto;
import com.spa.home_rental_application.review_service.Entities.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponseDto toResponse(Review r) {
        if (r == null) return null;
        return new ReviewResponseDto(
                r.getId(),
                r.getReviewerId(),
                r.getReviewerType(),
                r.getTargetId(),
                r.getTargetType(),
                r.getRating(),
                r.getTitle(),
                r.getBody(),
                r.getTags(),
                r.getIsVerified(),
                r.getIsModerated(),
                r.getModerationStatus(),
                r.getModerationReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
