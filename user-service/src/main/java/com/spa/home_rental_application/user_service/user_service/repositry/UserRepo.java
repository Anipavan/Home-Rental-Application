package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE u.isDeleted = false OR u.isDeleted IS NULL")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.id = :id AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<User> findActiveById(String id);

    Optional<User> findFirstByEmailIgnoreCaseAndIsDeletedFalse(String email);

    List<User> findByPhoneAndIsDeletedFalse(String phone);

    List<User> findByFirstNameContainingIgnoreCaseAndIsDeletedFalse(String firstName);

    Optional<User> findFirstByAuthUserIdAndIsDeletedFalse(String authUserId);

    boolean existsByEmailIgnoreCaseAndIsDeletedFalse(String email);

    /**
     * Idempotent KYC status update driven by {@code kyc-events}. Returns the
     * number of rows touched so callers can log a no-op when the userId is
     * unknown (event arrived before user-creation finished propagating).
     */
    @Modifying
    @Query("UPDATE User u SET u.kycStatus = :status, u.kycProvider = :provider, " +
            "u.kycVerifiedAt = :verifiedAt, u.updatedAt = :updatedAt WHERE u.id = :userId")
    int updateKycStatus(@Param("userId") String userId,
                        @Param("status") String status,
                        @Param("provider") String provider,
                        @Param("verifiedAt") LocalDateTime verifiedAt,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
