package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.SocietyCashfreeVendor;
import com.spa.home_rental_application.payment_service.payment_service.enums.CashfreeVendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Sibling of {@code CashfreeVendorRepository} — one row per SOCIETY
 * (keyed by building id) instead of per user. Read paths are the
 * same shape so the vendor-status admin dashboard can render both
 * columns of vendors side-by-side.
 */
public interface SocietyCashfreeVendorRepository
        extends JpaRepository<SocietyCashfreeVendor, String> {

    Optional<SocietyCashfreeVendor> findByBuildingId(String buildingId);

    Optional<SocietyCashfreeVendor> findByCashfreeVendorId(String cashfreeVendorId);

    List<SocietyCashfreeVendor> findByStatusInOrderByLastAttemptedAtAsc(
            List<CashfreeVendorStatus> statuses);
}
