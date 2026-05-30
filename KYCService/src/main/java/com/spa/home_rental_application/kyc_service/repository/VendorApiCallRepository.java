package com.spa.home_rental_application.kyc_service.repository;

import com.spa.home_rental_application.kyc_service.entity.VendorApiCall;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VendorApiCallRepository extends JpaRepository<VendorApiCall, String> {

    /**
     * Distinct vendor names ever recorded. Drives the admin dashboard's
     * vendor selector — we don't hard-code the list so a future
     * integration appears automatically once it's recorded its first
     * call.
     */
    @Query("SELECT DISTINCT v.vendorName FROM VendorApiCall v ORDER BY v.vendorName")
    List<String> findDistinctVendorNames();

    /**
     * Per-vendor aggregate over a time window. Returns rows of
     * [vendorName, status, count] so the controller can pivot them
     * into a per-vendor JSON shape without N+1 queries.
     */
    @Query("""
            SELECT v.vendorName, v.status, COUNT(v)
              FROM VendorApiCall v
             WHERE v.occurredAt >= :since
             GROUP BY v.vendorName, v.status
            """)
    List<Object[]> aggregateSince(@Param("since") LocalDateTime since);

    /**
     * Most recent BILLING_ALERT row per vendor — what the admin
     * dashboard shows as "last billing issue". Limit + sort live in
     * the caller's Pageable so the same query covers "give me the
     * last 5 alerts overall" and "give me the last 1 per vendor".
     */
    List<VendorApiCall> findByStatusOrderByOccurredAtDesc(
            VendorApiCall.Status status, Pageable pageable);

    /** Plain recency feed for the dashboard's "recent calls" table. */
    List<VendorApiCall> findByVendorNameOrderByOccurredAtDesc(
            String vendorName, Pageable pageable);
}
