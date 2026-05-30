package com.spa.home_rental_application.kyc_service.controller;

import com.spa.home_rental_application.kyc_service.entity.VendorApiCall;
import com.spa.home_rental_application.kyc_service.repository.VendorApiCallRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Admin-only dashboard for third-party vendor usage. Returns rolled-up
 * call counts + the most recent billing alerts so an operator can spot
 * "we ran out of Sandbox credits" or "Razorpay is rate-limiting us"
 * without diving into logs.
 *
 * <p>Gated to ADMIN via {@code @PreAuthorize} — the GatewayAuthFilter
 * stamps a Spring Security authority off the JWT role claim, and any
 * non-admin caller gets a 403 here. Same pattern other services use
 * for their admin-only endpoints.
 */
@RestController
@RequestMapping(value = "/kyc/admin/vendor-usage", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Vendor Usage",
        description = "Aggregate API usage + billing alerts for third-party vendors (admin only)")
public class AdminVendorUsageController {

    private final VendorApiCallRepository repo;

    @Operation(summary = "Per-vendor usage breakdown + recent billing alerts")
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        // Auth gate — programmatic check rather than @PreAuthorize because
        // kyc-service doesn't have @EnableMethodSecurity. The auth filter
        // (auth-commons) sets Spring Security authorities off the
        // X-Auth-Roles header the gateway stamps, so reading them here
        // is consistent + cheap. Matches the CallerSecurity.isAdmin()
        // pattern in payment-service.
        if (!isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admin role required");
        }

        // Time windows for the dashboard chips: today + last 30 days.
        // We aggregate both at the same query path — the result builder
        // then pivots into per-vendor JSON the frontend can render
        // without further bucketing.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        List<Object[]> today = repo.aggregateSince(startOfToday);
        List<Object[]> month = repo.aggregateSince(thirtyDaysAgo);

        Map<String, VendorSummary> byVendor = new TreeMap<>();
        accumulate(today, byVendor, /*scope*/ "today");
        accumulate(month, byVendor, /*scope*/ "month");

        // Latest 10 billing alerts across all vendors — what the admin
        // dashboard surfaces as "things you need to look at".
        List<VendorApiCall> recentAlerts = repo.findByStatusOrderByOccurredAtDesc(
                VendorApiCall.Status.BILLING_ALERT,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "occurredAt"))
        );

        // Convert to flat DTOs so the JSON shape is stable + small.
        List<Map<String, Object>> alertsDto = new ArrayList<>(recentAlerts.size());
        for (VendorApiCall a : recentAlerts) {
            Map<String, Object> row = new HashMap<>();
            row.put("vendorName", a.getVendorName());
            row.put("vendorEndpoint", a.getVendorEndpoint());
            row.put("errorCode", a.getErrorCode());
            row.put("errorMessage", a.getErrorMessage());
            row.put("occurredAt", a.getOccurredAt());
            alertsDto.add(row);
        }

        List<Map<String, Object>> vendorsDto = new ArrayList<>();
        for (Map.Entry<String, VendorSummary> e : byVendor.entrySet()) {
            VendorSummary s = e.getValue();
            Map<String, Object> row = new HashMap<>();
            row.put("vendorName", e.getKey());
            row.put("callsToday", s.callsToday);
            row.put("callsMonth", s.callsMonth);
            row.put("successToday", s.successToday);
            row.put("userErrorsToday", s.userErrorsToday);
            row.put("billingAlertsMonth", s.billingAlertsMonth);
            row.put("outagesMonth", s.outagesMonth);
            vendorsDto.add(row);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("generatedAt", now);
        response.put("vendors", vendorsDto);
        response.put("recentBillingAlerts", alertsDto);
        return ResponseEntity.ok(response);
    }

    /* ─────────────────────────── helpers ─────────────────────────── */

    /** Aggregates one (vendor, status, count) row triple into the
     *  per-vendor summary map, respecting the time window scope. */
    private void accumulate(List<Object[]> rows,
                            Map<String, VendorSummary> map,
                            String scope) {
        for (Object[] r : rows) {
            String vendor = (String) r[0];
            VendorApiCall.Status status = (VendorApiCall.Status) r[1];
            long count = ((Number) r[2]).longValue();
            VendorSummary s = map.computeIfAbsent(vendor, v -> new VendorSummary());
            if ("today".equals(scope)) {
                s.callsToday += count;
                if (status == VendorApiCall.Status.SUCCESS) s.successToday += count;
                if (status == VendorApiCall.Status.USER_ERROR) s.userErrorsToday += count;
            } else {
                s.callsMonth += count;
                if (status == VendorApiCall.Status.BILLING_ALERT) s.billingAlertsMonth += count;
                if (status == VendorApiCall.Status.OUTAGE) s.outagesMonth += count;
            }
        }
    }

    /** Mutable accumulator while we walk the two aggregate queries. */
    private static final class VendorSummary {
        long callsToday;
        long callsMonth;
        long successToday;
        long userErrorsToday;
        long billingAlertsMonth;
        long outagesMonth;
    }

    /** True when the current security context has an ADMIN authority.
     *  Accepts either "ADMIN" or "ROLE_ADMIN" so we tolerate whichever
     *  case the auth filter happens to stamp. */
    private static boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }
}
