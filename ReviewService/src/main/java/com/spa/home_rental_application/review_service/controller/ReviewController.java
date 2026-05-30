package com.spa.home_rental_application.review_service.controller;

import com.spa.home_rental_application.review_service.DTO.Request.CreateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Request.ModerateReviewRequest;
import com.spa.home_rental_application.review_service.DTO.Response.RatingSummaryDto;
import com.spa.home_rental_application.review_service.DTO.Response.ReviewResponseDto;
import com.spa.home_rental_application.review_service.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Reviews", description = "Tenant + owner + property reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Submit a review (publishes review.submitted)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> submit(@Valid @RequestBody CreateReviewRequest request) {
        log.info("POST /reviews target={}/{} rating={}",
                request.targetType(), request.targetId(), request.rating());
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.submit(request));
    }

    @Operation(summary = "Get a review by id")
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponseDto> getById(@PathVariable("id") String reviewId) {
        return ResponseEntity.ok(reviewService.getById(reviewId));
    }

    @Operation(summary = "Reviews for a property (paginated)")
    @GetMapping("/property/{propertyId}")
    public ResponseEntity<Page<ReviewResponseDto>> propertyReviews(
            @PathVariable String propertyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.listByTarget("PROPERTY", propertyId, p));
    }

    @Operation(summary = "Reviews about an owner")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<Page<ReviewResponseDto>> ownerReviews(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.listByTarget("OWNER", ownerId, p));
    }

    @Operation(summary = "Reviews about a tenant")
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<Page<ReviewResponseDto>> tenantReviews(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.listByTarget("TENANT", tenantId, p));
    }

    @Operation(summary = "Reviews authored by a reviewer")
    @GetMapping("/by/{reviewerId}")
    public ResponseEntity<Page<ReviewResponseDto>> byReviewer(
            @PathVariable String reviewerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.listByReviewer(reviewerId, p));
    }

    @Operation(summary = "Pending-moderation queue (admin only)")
    @GetMapping("/moderation/pending")
    public ResponseEntity<Page<ReviewResponseDto>> pendingModeration(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(reviewService.listPendingModeration(p));
    }

    @Operation(summary = "Featured testimonials for the public landing page")
    @GetMapping("/featured")
    public ResponseEntity<Page<ReviewResponseDto>> featured(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "3") @Min(1) int size) {
        // No auth required. Service layer enforces APPROVED + sort order;
        // here we only pass the page + size budget. Sort is intentionally
        // ignored (service overrides) so a future query-string can't
        // jailbreak the testimonials surface.
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(reviewService.listFeaturedForLandingPage(p));
    }

    @Operation(summary = "Admin: approve / reject / flag a review")
    @PutMapping(value = "/{id}/moderate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> moderate(
            @PathVariable("id") String reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        log.info("PUT /reviews/{}/moderate decision={}", reviewId, request.decision());
        return ResponseEntity.ok(reviewService.moderate(reviewId, request));
    }

    @Operation(summary = "Soft-delete a review")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable("id") String reviewId) {
        log.info("DELETE /reviews/{}", reviewId);
        reviewService.softDelete(reviewId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Aggregate rating + histogram for a target")
    @GetMapping("/summary/{targetType}/{targetId}")
    public ResponseEntity<RatingSummaryDto> summary(@PathVariable String targetType,
                                                    @PathVariable String targetId) {
        return ResponseEntity.ok(reviewService.getSummary(targetType, targetId));
    }
}
