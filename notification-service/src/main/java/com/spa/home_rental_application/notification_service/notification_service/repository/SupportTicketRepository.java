package com.spa.home_rental_application.notification_service.notification_service.repository;

import com.spa.home_rental_application.notification_service.notification_service.entities.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportTicketRepository extends MongoRepository<SupportTicket, String> {

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Property-related enquiries fanned out to a specific owner. */
    Page<SupportTicket> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);

    long countByStatus(String status);

    /** Open ticket count for the owner-side inbox badge. */
    long countByOwnerIdAndStatus(String ownerId, String status);
}
