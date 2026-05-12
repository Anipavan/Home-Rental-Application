package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Audit H24: next batch of unpublished events, oldest first.
     * Capped via {@link Pageable} so a backlogged broker doesn't make
     * one publish-cycle pull every pending row. {@code attempts} cap
     * keeps poison-pill rows out of the next batch after N failures
     * — they're left at FAILED for ops drilling.
     */
    @Query("SELECT e FROM OutboxEvent e " +
           "WHERE e.status = 'PENDING' AND e.attempts < :maxAttempts " +
           "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);
}
