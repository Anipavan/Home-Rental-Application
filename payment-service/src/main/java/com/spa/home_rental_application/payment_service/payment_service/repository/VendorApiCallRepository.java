package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.VendorApiCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Write-only repository for the shared {@code vendor_api_calls} table.
 * Payment-service produces rows; the admin dashboard in kyc-service
 * reads them via its own copy of this entity. Intentionally bare —
 * we never list or aggregate from here.
 */
@Repository
public interface VendorApiCallRepository extends JpaRepository<VendorApiCall, String> {
}
