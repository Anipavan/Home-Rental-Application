package com.spa.home_rental_application.property_service.property_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client used by the rental-agreement PDF generator to enrich the
 * deed with the parties' KYC names + permanent addresses pulled from
 * user-service. Failures are absorbed by {@link UserClientFallback} so a
 * user-service outage never blocks deed generation — the PDF just renders
 * with handwritten blanks where names would have gone.
 */
@FeignClient(name = "HRA-user-service", fallback = UserClientFallback.class)
public interface UserClient {

    /**
     * Mirrors user-service {@code GET /users/user/{userId}}. We intentionally
     * declare a local DTO with only the fields the deed needs — keeps the
     * services loosely coupled (extra fields on the user-service response
     * are ignored on deserialization).
     */
    @GetMapping("/users/user/{userId}")
    UserSummary getUserById(@PathVariable("userId") String userId);

    /**
     * Lookup by auth-service user id, used when we have the {@code ownerId}
     * stored on a Building (which is the {@code authUserId}, not the
     * user-service surrogate id). Routes to user-service
     * {@code GET /users/auth/{authUserId}}. Used by the building lookup
     * path to populate the "verified owner" badge on public listings.
     */
    @GetMapping("/users/auth/{authUserId}")
    UserSummary getUserByAuthId(@PathVariable("authUserId") String authUserId);

    /** Subset of {@code user-service UserResponseDto} used by the deed renderer
     *  and the verified-owner badge. New fields go on the end — Jackson on the
     *  receiving side ignores unknown fields, but Java records are positional
     *  so adding mid-record would break the deed renderer's existing usage. */
    record UserSummary(
            String id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String address,
            /** PENDING | INITIATED | VERIFIED | FAILED. Null when the
             *  Feign call failed (fallback path) — treat as not-verified. */
            String kycStatus
    ) {
        /** Empty placeholder used when the user-service call fails or returns null. */
        public static UserSummary empty() {
            return new UserSummary(null, null, null, null, null, null, null);
        }

        public String fullName() {
            String f = firstName == null ? "" : firstName.trim();
            String l = lastName == null ? "" : lastName.trim();
            String joined = (f + " " + l).trim();
            return joined.isEmpty() ? null : joined;
        }

        /** True only when KYC is explicitly VERIFIED. Any other value
         *  (PENDING, INITIATED, FAILED, null, missing) is treated as
         *  "not verified" so the badge defaults to off. */
        public boolean isVerified() {
            return "VERIFIED".equalsIgnoreCase(kycStatus);
        }
    }
}
