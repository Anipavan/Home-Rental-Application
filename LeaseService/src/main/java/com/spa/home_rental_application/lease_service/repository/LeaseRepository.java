package com.spa.home_rental_application.lease_service.repository;

import com.spa.home_rental_application.lease_service.Entities.Lease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaseRepository extends JpaRepository<Lease, String> {

    Optional<Lease> findByLeaseNumber(String leaseNumber);

    List<Lease> findByTenantId(String tenantId);

    List<Lease> findByFlatId(String flatId);

    List<Lease> findByOwnerId(String ownerId);

    /**
     * Active leases that end on or before {@code threshold} and have not yet
     * received an expiry warning. Used by the daily cron.
     */
    @Query("SELECT l FROM Lease l " +
            "WHERE l.status = 'ACTIVE' " +
            "AND l.endDate <= :threshold " +
            "AND l.endDate >= :today " +
            "AND l.expiryWarningSentAt IS NULL")
    List<Lease> findExpiringWithoutWarning(@Param("today") LocalDate today,
                                           @Param("threshold") LocalDate threshold);

    /** Active leases whose end date has already passed — flipped to EXPIRED. */
    @Query("SELECT l FROM Lease l WHERE l.status = 'ACTIVE' AND l.endDate < :today")
    List<Lease> findActivePastEndDate(@Param("today") LocalDate today);
}
