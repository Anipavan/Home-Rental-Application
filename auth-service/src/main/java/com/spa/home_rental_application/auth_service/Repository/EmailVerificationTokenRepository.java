package com.spa.home_rental_application.auth_service.Repository;

import com.spa.home_rental_application.auth_service.Entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Used by the resend flow: invalidate every still-usable token for
     * the user before minting a new one, so an old link in their inbox
     * can't be replayed after a resend.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmailVerificationToken t SET t.consumedAt = :now " +
           "  WHERE t.userId = :userId AND t.consumedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId,
                             @Param("now") Instant now);

    /**
     * Rate-limit helper: count tokens minted for this user in the
     * trailing window. The verification controller refuses to mint
     * a fresh one when this exceeds the per-window cap.
     */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /**
     * Janitor sweep — wipes expired rows older than the TTL. Indexed
     * on expires_at so the WHERE clause is a ranged scan.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteAllExpired(@Param("cutoff") Instant cutoff);

    List<EmailVerificationToken> findByUserIdOrderByCreatedAtDesc(Long userId);
}
