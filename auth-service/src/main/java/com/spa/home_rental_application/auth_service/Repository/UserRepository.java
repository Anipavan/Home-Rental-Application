package com.spa.home_rental_application.auth_service.Repository;

import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserDetails, Long> {

    Optional<UserDetails> findByUserName(String userName);

    Optional<UserDetails> findByEmailIgnoreCase(String email);

    boolean existsByUserName(String userName);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Used at registration to enforce the same one-account-per-phone
     * rule we have for email. The phone is normalised to E.164 in
     * AuthServiceImpl.register BEFORE the check, so the comparison
     * is a straight exact match (no IgnoreCase / no LIKE — every
     * caller already canonicalises).
     */
    boolean existsByPhone(String phone);

    List<UserDetails> findByUserRole(Roles userRole);

    /**
     * Sweeper used by {@link
     * com.spa.home_rental_application.auth_service.scheduler.OrphanRegistrationCleanupScheduler}.
     * Deletes rows that started the paid-maintainer flow, were
     * persisted as {@code enabled=false,
     * disable_reason='REGISTRATION_PAYMENT_PENDING'}, but never
     * completed payment within {@code cutoff}. Lets the same email
     * / phone / username be reused on a fresh signup attempt.
     *
     * <p>Returns the affected row count so the scheduler can log
     * useful metrics. The JPQL form is preferred over a derived
     * delete-method because Spring's derived deletes don't return
     * a count and don't compose well with {@code Instant} comparisons.
     */
    @Modifying
    @Query("DELETE FROM UserDetails u " +
            "WHERE u.enabled = false " +
            "  AND u.disableReason = 'REGISTRATION_PAYMENT_PENDING' " +
            "  AND u.recordCreatedDate < :cutoff")
    int deleteOrphanedPaymentPendingRows(@Param("cutoff") Instant cutoff);
}
