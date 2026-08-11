package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.CashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashfreeVendorRepository extends JpaRepository<CashfreeVendor, String> {

    /** The one row for this owner. Absent when we haven't started registration yet. */
    Optional<CashfreeVendor> findByUserId(String userId);

    /** Look up by Cashfree's opaque id — used by the webhook handler. */
    Optional<CashfreeVendor> findByCashfreeVendorId(String cashfreeVendorId);

    /**
     * All vendors in any of the given statuses, ordered by last-attempted
     * so retry sweeps prioritise the ones that have been stalled longest.
     * Powers the admin dashboard's status table.
     */
    List<CashfreeVendor> findByStatusInOrderByLastAttemptedAtAsc(List<CashfreeVendorStatus> statuses);
}
