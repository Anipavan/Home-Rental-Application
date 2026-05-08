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

    /** Subset of {@code user-service UserResponseDto} used by the deed renderer. */
    record UserSummary(
            String id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String address
    ) {
        /** Empty placeholder used when the user-service call fails or returns null. */
        public static UserSummary empty() {
            return new UserSummary(null, null, null, null, null, null);
        }

        public String fullName() {
            String f = firstName == null ? "" : firstName.trim();
            String l = lastName == null ? "" : lastName.trim();
            String joined = (f + " " + l).trim();
            return joined.isEmpty() ? null : joined;
        }
    }
}
