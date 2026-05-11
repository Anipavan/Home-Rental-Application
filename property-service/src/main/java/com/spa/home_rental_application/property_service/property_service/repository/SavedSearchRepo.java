package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedSearchRepo extends JpaRepository<SavedSearch, String> {

    /** A user's saved searches, newest-first for the list UI. */
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Active rows the scheduler should consider on each tick. The
     * "active" filter is at the DB level so a long-disabled search
     * doesn't even materialise into memory.
     */
    List<SavedSearch> findByIsActiveTrue();

    long deleteByIdAndUserId(String id, String userId);
}
