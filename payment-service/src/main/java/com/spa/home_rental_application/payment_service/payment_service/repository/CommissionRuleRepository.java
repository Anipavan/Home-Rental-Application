package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Commission-rule lookups. The service layer wraps these to enforce
 * the "one global row / at most one row per owner" invariant that
 * Oracle's unique index can't express in a null-friendly way.
 */
@Repository
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, String> {

    /**
     * The single global-default row (or empty if none seeded yet — should
     * only happen if the V6 seed failed for some reason). Returned first
     * from the two-tier lookup in
     * {@code CommissionServiceImpl.rateBpsFor}.
     */
    @Query("SELECT r FROM CommissionRule r WHERE r.ownerId IS NULL")
    Optional<CommissionRule> findGlobal();

    /** The per-owner override, if one exists. */
    Optional<CommissionRule> findByOwnerId(String ownerId);

    /**
     * All per-owner overrides for the admin dashboard's "Overrides"
     * table. Ordered by owner id so re-renders don't jitter.
     */
    @Query("SELECT r FROM CommissionRule r WHERE r.ownerId IS NOT NULL ORDER BY r.ownerId ASC")
    List<CommissionRule> findAllOverrides();
}
