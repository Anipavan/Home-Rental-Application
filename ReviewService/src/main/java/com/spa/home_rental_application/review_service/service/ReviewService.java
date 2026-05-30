package com.spa.home_rental_application.review_service.service;

import com.spa.home_rental_application.review_service.DTO.Request.CreateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Request.ModerateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Response.RatingSummaryDto;
import com.spa.home_rental_application.review_service.DTO.Response.ReviewResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponseDto submit(CreateReviewRequest request);

    ReviewResponseDto getById(String reviewId);

    Page<ReviewResponseDto> listByTarget(String targetType, String targetId, Pageable pageable);

    Page<ReviewResponseDto> listByReviewer(String reviewerId, Pageable pageable);

    Page<ReviewResponseDto> listPendingModeration(Pageable pageable);

    /**
     * Public landing-page testimonials. Returns APPROVED reviews with a
     * usable body, sorted by rating desc then createdAt desc so the
     * highest-rated, newest reviews surface first.
     *
     * <p>No auth required — used by the public landing page. Returns an
     * empty page when no reviews qualify; the caller (landing) hides the
     * testimonials section entirely rather than fabricating content.
     */
    Page<ReviewResponseDto> listFeaturedForLandingPage(Pageable pageable);

    ReviewResponseDto moderate(String reviewId, ModerateReviewRequest request);

    void softDelete(String reviewId);

    RatingSummaryDto getSummary(String targetType, String targetId);
}
