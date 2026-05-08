package com.spa.home_rental_application.review_service.repository;

import com.spa.home_rental_application.review_service.Entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    Page<Review> findByTargetTypeAndTargetIdAndIsDeletedFalse(String targetType,
                                                              String targetId,
                                                              Pageable pageable);

    List<Review> findByTargetTypeAndTargetIdAndIsDeletedFalseAndModerationStatus(
            String targetType, String targetId, String moderationStatus);

    Page<Review> findByReviewerIdAndIsDeletedFalse(String reviewerId, Pageable pageable);

    Optional<Review> findByIdAndIsDeletedFalse(String id);

    Page<Review> findByModerationStatusAndIsDeletedFalse(String moderationStatus, Pageable pageable);

    long countByTargetTypeAndTargetIdAndIsDeletedFalse(String targetType, String targetId);
}
