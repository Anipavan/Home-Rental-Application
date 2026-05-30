package com.spa.home_rental_application.kyc_service.service;

import com.spa.home_rental_application.kyc_service.entity.VendorApiCall;
import com.spa.home_rental_application.kyc_service.repository.VendorApiCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Single entry-point for "we just called a third-party vendor; record
 * what happened". Used by SandboxKycProvider today; new vendors call
 * this same method with their own vendor name + status mapping.
 *
 * <p>The record is written synchronously — a single insert is <5ms,
 * and adding {@code @EnableAsync} to the whole service just to save
 * that on the PAN-verify hot path isn't worth the config surface.
 * A persistence failure logs and swallows — vendor-usage tracking
 * should never break a real user flow, that's worse than missing
 * one row in the dashboard.
 *
 * <p>Truncates the error message to the column width (1024 chars)
 * defensively — some vendor 5xx bodies are HTML pages with stack
 * traces, and a truncated row is infinitely better than a SQL
 * exception that loses the whole record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VendorUsageRecorder {

    private static final int MAX_MESSAGE_LENGTH = 1024;

    private final VendorApiCallRepository repo;

    /**
     * Persist a single vendor call. Fire-and-forget — no return value
     * because callers never need to act on the persistence result.
     *
     * @param vendorName        e.g. "SANDBOX_NSDL_PAN"
     * @param vendorEndpoint    the vendor's URL path, for the admin
     *                          tooltip ("/kyc/pan/verify")
     * @param status            one of VendorApiCall.Status — caller
     *                          maps their exception to this enum
     * @param errorCode         HTTP status code as a string, or null
     *                          for transport-level failures
     * @param errorMessage      vendor's error message verbatim, or
     *                          null for success
     * @param responseTimeMs    round-trip latency; null when unknown
     * @param triggeredByUserId authUserId of the user whose action
     *                          triggered this call; null for system jobs
     */
    public void record(
            String vendorName,
            String vendorEndpoint,
            VendorApiCall.Status status,
            String errorCode,
            String errorMessage,
            Integer responseTimeMs,
            String triggeredByUserId
    ) {
        try {
            VendorApiCall call = VendorApiCall.builder()
                    .vendorName(vendorName)
                    .vendorEndpoint(vendorEndpoint)
                    .status(status)
                    .errorCode(errorCode)
                    .errorMessage(truncate(errorMessage))
                    .occurredAt(LocalDateTime.now())
                    .responseTimeMs(responseTimeMs)
                    .triggeredByUserId(triggeredByUserId)
                    .build();
            repo.save(call);
        } catch (Exception e) {
            // Never let a tracking failure poison the real user request.
            // Log and move on — the metric is missing but the user got
            // their verification result.
            log.warn("Failed to record vendor call ({}/{}): {}",
                    vendorName, status, e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_MESSAGE_LENGTH
                ? s
                : s.substring(0, MAX_MESSAGE_LENGTH);
    }
}
