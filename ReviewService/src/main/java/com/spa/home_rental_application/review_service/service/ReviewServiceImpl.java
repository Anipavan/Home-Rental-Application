package com.spa.home_rental_application.review_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewModeratedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.ReviewServiceEvents.ReviewSubmittedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.ReviewServiceEvents;
import com.spa.home_rental_application.review_service.DTO.Request.CreateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Request.ModerateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Response.RatingSummaryDto;
import com.spa.home_rental_application.review_service.DTO.Response.ReviewResponseDto;
import com.spa.home_rental_application.review_service.Entities.Review;
import com.spa.home_rental_application.review_service.Exceptionclass.InvalidReviewException;
import com.spa.home_rental_application.review_service.Exceptionclass.ReviewNotFoundException;
import com.spa.home_rental_application.review_service.client.PropertyClient;
import com.spa.home_rental_application.review_service.config.ReviewProperties;
import com.spa.home_rental_application.review_service.mapper.ReviewMapper;
import com.spa.home_rental_application.review_service.repository.ReviewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private static final Set<String> ALLOWED_REVIEWER_TYPES = Set.of("TENANT", "OWNER");
    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("PROPERTY", "OWNER", "TENANT");
    private static final Set<String> ALLOWED_DECISIONS = Set.of("APPROVED", "REJECTED", "FLAGGED");

    private final ReviewRepository reviewRepository;
    private final ReviewMapper mapper;
    private final ReviewServiceEvents events;
    private final ReviewProperties props;
    /**
     * Used to resolve a building's ownerId when a tenant leaves a
     * PROPERTY-targeted review. Failures fall through to a null
     * BuildingSummary via {@link com.spa.home_rental_application.review_service.client.PropertyClientFallback},
     * in which case the published event carries
     * {@code ownerAuthId = null} and the notification service simply
     * skips the owner-side email rather than blowing up.
     */
    private final PropertyClient propertyClient;

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             ReviewMapper mapper,
                             ReviewServiceEvents events,
                             ReviewProperties props,
                             PropertyClient propertyClient) {
        this.reviewRepository = reviewRepository;
        this.mapper = mapper;
        this.events = events;
        this.props = props;
        this.propertyClient = propertyClient;
    }

    @Override
    public ReviewResponseDto submit(CreateReviewRequest req) {
        log.info("Submit review reviewer={} target={}/{} rating={}",
                req.reviewerId(), req.targetType(), req.targetId(), req.rating());
        validateEnum("reviewerType", req.reviewerType(), ALLOWED_REVIEWER_TYPES);
        validateEnum("targetType", req.targetType(), ALLOWED_TARGET_TYPES);

        boolean autoApprove = props.isAutoPublish();
        Review review = Review.builder()
                .reviewerId(req.reviewerId())
                .reviewerType(req.reviewerType().toUpperCase())
                .targetId(req.targetId())
                .targetType(req.targetType().toUpperCase())
                .rating(req.rating())
                .title(req.title())
                .body(req.body())
                .tags(req.tags() == null ? List.of() : req.tags())
                .isModerated(autoApprove)
                .moderationStatus(autoApprove ? "APPROVED" : "PENDING")
                .build();
        Review saved = reviewRepository.save(review);

        // Resolve the owner's auth user id so the downstream
        // notification listener can fan the email straight to them.
        // For PROPERTY reviews: targetId is a buildingId — call
        //   property-service via Feign to read Building.ownerId.
        // For OWNER reviews: the targetId IS the owner's auth id.
        // For TENANT reviews: leave ownerAuthId null — the
        //   notification flow currently skips that case (no
        //   tenant-side reviews surface), so we'd just be filling
        //   the field with junk.
        String ownerAuthId = null;
        String targetType = saved.getTargetType();
        if ("PROPERTY".equalsIgnoreCase(targetType)) {
            try {
                PropertyClient.BuildingSummary b =
                        propertyClient.getBuildingById(saved.getTargetId());
                if (b != null && b.ownerId() != null && !b.ownerId().isBlank()) {
                    ownerAuthId = b.ownerId();
                } else {
                    log.warn("Building lookup returned null/empty ownerId for buildingId={}",
                            saved.getTargetId());
                }
            } catch (Exception ex) {
                // Defensive — the fallback bean already absorbs Feign
                // failures, but a configuration error (missing
                // bean / unrecognised path) would otherwise propagate
                // out of the review-submit flow and tank the review.
                // Keep the review side alive; just lose the email.
                log.warn("Couldn't resolve building owner for review={}: {}",
                        saved.getId(), ex.getMessage());
            }
        } else if ("OWNER".equalsIgnoreCase(targetType)) {
            ownerAuthId = saved.getTargetId();
        }

        // Short-form comment for the owner email's inline blockquote.
        // Truncated to a tweet-sized excerpt so big reviews don't
        // blow up the email rendering; full text stays in the app.
        String commentExcerpt = saved.getBody();
        if (commentExcerpt != null && commentExcerpt.length() > 280) {
            commentExcerpt = commentExcerpt.substring(0, 277) + "…";
        }

        events.sendReviewSubmitted(ReviewSubmittedEvent.builder()
                .eventType("review.submitted")
                .reviewId(saved.getId())
                .reviewerId(saved.getReviewerId())
                .reviewerType(saved.getReviewerType())
                .targetId(saved.getTargetId())
                .targetType(saved.getTargetType())
                .rating(saved.getRating())
                .ownerAuthId(ownerAuthId)
                .comment(commentExcerpt)
                .createdAt(saved.getCreatedAt())
                .timestamp(LocalDateTime.now())
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    public ReviewResponseDto getById(String reviewId) {
        return reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ReviewNotFoundException("No review with id=" + reviewId));
    }

    @Override
    public Page<ReviewResponseDto> listByTarget(String targetType, String targetId, Pageable pageable) {
        validateEnum("targetType", targetType, ALLOWED_TARGET_TYPES);
        return reviewRepository
                .findByTargetTypeAndTargetIdAndIsDeletedFalse(targetType.toUpperCase(), targetId, pageable)
                .map(mapper::toResponse);
    }

    @Override
    public Page<ReviewResponseDto> listByReviewer(String reviewerId, Pageable pageable) {
        return reviewRepository.findByReviewerIdAndIsDeletedFalse(reviewerId, pageable)
                .map(mapper::toResponse);
    }

    @Override
    public Page<ReviewResponseDto> listPendingModeration(Pageable pageable) {
        return reviewRepository.findByModerationStatusAndIsDeletedFalse("PENDING", pageable)
                .map(mapper::toResponse);
    }

    @Override
    public ReviewResponseDto moderate(String reviewId, ModerateReviewRequest req) {
        validateEnum("decision", req.decision(), ALLOWED_DECISIONS);
        Review review = reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("No review with id=" + reviewId));
        review.setIsModerated(true);
        review.setModerationStatus(req.decision().toUpperCase());
        review.setModerationReason(req.reason());
        review.setModeratedBy(req.moderatorId());
        review.setModeratedAt(LocalDateTime.now());
        Review saved = reviewRepository.save(review);

        events.sendReviewModerated(ReviewModeratedEvent.builder()
                .eventType("review.moderated")
                .reviewId(saved.getId())
                .moderatorId(req.moderatorId())
                .moderationDecision(saved.getModerationStatus())
                .moderationReason(saved.getModerationReason())
                .moderatedAt(saved.getModeratedAt())
                .timestamp(LocalDateTime.now())
                .build());
        return mapper.toResponse(saved);
    }

    @Override
    public void softDelete(String reviewId) {
        Review review = reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("No review with id=" + reviewId));
        review.setIsDeleted(true);
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }

    @Override
    public RatingSummaryDto getSummary(String targetType, String targetId) {
        validateEnum("targetType", targetType, ALLOWED_TARGET_TYPES);
        // Cap at 1000 reviews per summary — pagination is the right move at scale,
        // but summary needs the full distribution today.
        List<Review> rows = reviewRepository
                .findByTargetTypeAndTargetIdAndIsDeletedFalseAndModerationStatus(
                        targetType.toUpperCase(), targetId, "APPROVED");
        long total = rows.size();
        Map<Integer, Long> histogram = new LinkedHashMap<>();
        for (int r = 5; r >= 1; r--) histogram.put(r, 0L);
        double sum = 0;
        for (Review r : rows) {
            histogram.merge(r.getRating(), 1L, Long::sum);
            sum += r.getRating();
        }
        double avg = total == 0 ? 0.0 : sum / total;
        return new RatingSummaryDto(targetId, targetType.toUpperCase(), total,
                Math.round(avg * 100.0) / 100.0,
                new HashMap<>(histogram));
    }

    private void validateEnum(String field, String value, Set<String> allowed) {
        if (value == null || !allowed.contains(value.toUpperCase())) {
            throw new InvalidReviewException(
                    field + " must be one of " + allowed + " (got " + value + ")",
                    "INVALID_" + field.toUpperCase());
        }
    }
}
