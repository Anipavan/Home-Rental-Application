package com.spa.home_rental_application.notification_service.notification_service.repository;

import com.spa.home_rental_application.notification_service.notification_service.entities.VisitRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface VisitRequestRepository extends MongoRepository<VisitRequest, String> {

    Page<VisitRequest> findByStatusOrderByPreferredAtAsc(String status, Pageable pageable);

    Page<VisitRequest> findByFlatIdOrderByPreferredAtAsc(String flatId, Pageable pageable);

    Page<VisitRequest> findByUserIdOrderByPreferredAtDesc(String userId, Pageable pageable);

    /** Visit requests targeting an owner's buildings. Powers /owner/enquiries. */
    Page<VisitRequest> findByOwnerIdOrderByPreferredAtAsc(String ownerId, Pageable pageable);

    /**
     * Date-range slice keyed on {@code preferred_at}. Powers the admin
     * "today / this week" calendar filter.
     */
    Page<VisitRequest> findByPreferredAtBetweenOrderByPreferredAtAsc(
            Instant from, Instant to, Pageable pageable);

    long countByStatus(String status);

    /** Pending count for the owner-side inbox badge. */
    long countByOwnerIdAndStatus(String ownerId, String status);
}
