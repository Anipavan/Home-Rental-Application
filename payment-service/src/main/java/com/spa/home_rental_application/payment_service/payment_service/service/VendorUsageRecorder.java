package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.entities.VendorApiCall;
import com.spa.home_rental_application.payment_service.payment_service.repository.VendorApiCallRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records one row per outbound Razorpay (or future payment-side
 * vendor) API call into the shared {@code vendor_api_calls} table.
 * The admin Vendor Usage dashboard in kyc-service reads from this
 * table and shows aggregated counts, recent billing alerts, etc.
 *
 * <p>The recorder is intentionally fire-and-forget: every save is
 * wrapped in a try/catch so a transient JDBC blip never breaks the
 * actual payment flow. Worst case we lose visibility into one call;
 * the user still gets a successful (or properly-failed) checkout.
 *
 * <p>Mirrors {@code com.spa.home_rental_application.kyc_service.service.VendorUsageRecorder}
 * — keep them in lockstep when the schema evolves.
 */
@Service
@Slf4j
public class VendorUsageRecorder {

    /** Hibernate column cap. Truncate so a verbose Razorpay 4xx body
     *  doesn't break the INSERT. */
    private static final int MAX_ERROR_MESSAGE_LEN = 1024;

    private final VendorApiCallRepository repo;

    public VendorUsageRecorder(VendorApiCallRepository repo) {
        this.repo = repo;
    }

    /**
     * Persist a single vendor call record. Synchronous (the table is
     * single-INSERT-per-call and ~1ms in steady state). Drop @Async
     * intentionally — payment-service lacks {@code @EnableAsync}, so
     * marking this @Async would silently run it on the caller thread
     * anyway. If we ever turn on async, switch to a dedicated executor
     * so the payment flow stays off the I/O critical path.
     */
    public void record(String vendorName,
                       String vendorEndpoint,
                       VendorApiCall.Status status,
                       String errorCode,
                       String errorMessage,
                       Integer responseTimeMs,
                       String triggeredByUserId) {
        try {
            VendorApiCall row = VendorApiCall.builder()
                    .vendorName(vendorName)
                    .vendorEndpoint(vendorEndpoint)
                    .status(status)
                    .errorCode(errorCode)
                    .errorMessage(truncate(errorMessage))
                    .occurredAt(LocalDateTime.now())
                    .responseTimeMs(responseTimeMs)
                    .triggeredByUserId(triggeredByUserId)
                    .build();
            repo.save(row);
        } catch (Exception ex) {
            // Never abort the payment flow over a missing audit row.
            // Log loudly so the operator sees it (and so the rate is
            // visible in observability if the DB really is down).
            log.warn("VendorUsageRecorder: failed to persist {} call ({} status={}): {}",
                    vendorName, vendorEndpoint, status, ex.getMessage());
        }
    }

    private static String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() <= MAX_ERROR_MESSAGE_LEN
                ? msg
                : msg.substring(0, MAX_ERROR_MESSAGE_LEN);
    }
}
