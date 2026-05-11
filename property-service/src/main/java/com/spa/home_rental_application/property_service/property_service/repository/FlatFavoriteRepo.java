package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.FlatFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlatFavoriteRepo extends JpaRepository<FlatFavorite, String> {

    /**
     * Lookup powering the heart-toggle state: "is this user already
     * favouriting this flat?". Returns Optional so the delete path
     * can be a no-op when the row's already gone.
     */
    Optional<FlatFavorite> findByUserIdAndFlatId(String userId, String flatId);

    /**
     * Used by the wishlist page — newest first so the most recent save
     * floats to the top. We hydrate the corresponding FlatResponseDTOs
     * in the service layer.
     */
    List<FlatFavorite> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Lightweight projection for the browse/detail page heart-icon
     * state: returns just the flatIds the user has saved. Avoids
     * hydrating the join when all the FE wants is "is X in the set?".
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT f.flatId FROM FlatFavorite f WHERE f.userId = :userId"
    )
    List<String> findFlatIdsByUserId(String userId);

    /** Used by the toggle endpoint to make removal idempotent. */
    long deleteByUserIdAndFlatId(String userId, String flatId);

    /** Optional analytics — popularity count per flat. */
    long countByFlatId(String flatId);
}
