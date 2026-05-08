package com.spa.home_rental_application.kyc_service.repository;

import com.spa.home_rental_application.kyc_service.Entities.KycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface KycRepository extends JpaRepository<KycRecord, String> {

    Optional<KycRecord> findByUserId(String userId);

    Optional<KycRecord> findByKycReferenceId(String referenceId);

    boolean existsByUserId(String userId);

    @Modifying
    @Query("UPDATE KycRecord k SET k.verificationStatus = :status, " +
            "k.verifiedAt = :verifiedAt, k.updatedAt = :updatedAt WHERE k.userId = :userId")
    int updateStatus(@Param("userId") String userId,
                     @Param("status") String status,
                     @Param("verifiedAt") LocalDateTime verifiedAt,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
