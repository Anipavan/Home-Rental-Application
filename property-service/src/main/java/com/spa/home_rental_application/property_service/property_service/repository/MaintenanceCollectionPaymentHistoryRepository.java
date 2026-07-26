package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.MaintenanceCollectionPaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MaintenanceCollectionPaymentHistoryRepository
        extends JpaRepository<MaintenanceCollectionPaymentHistory, String> {

    /** History rows for a single collection, newest link first. */
    List<MaintenanceCollectionPaymentHistory> findByCollectionIdOrderByLinkedAtDesc(
            String collectionId);

    /** Batch lookup — one query for the whole Flat charges page.
     *  Used by the maintainer-dashboard enrichment to fetch every
     *  historical payment link for every visible collection row in
     *  one shot instead of N per-row queries. */
    List<MaintenanceCollectionPaymentHistory> findByCollectionIdIn(
            Collection<String> collectionIds);

    /** Existence check used before inserting to swallow duplicate-
     *  pair attempts (idempotent bridge calls). Cheaper than
     *  catching DataIntegrityViolationException on save(). */
    boolean existsByCollectionIdAndPaymentId(
            String collectionId, String paymentId);
}
