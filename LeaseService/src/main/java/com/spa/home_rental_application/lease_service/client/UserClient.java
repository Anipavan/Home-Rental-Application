package com.spa.home_rental_application.lease_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client used by the lease-deed PDF generator to enrich the deed with
 * the parties' KYC names + permanent addresses pulled from user-service.
 * Failures are absorbed by {@link UserClientFallback} so a user-service
 * outage never blocks deed generation.
 */
@FeignClient(name = "HRA-user-service", fallback = UserClientFallback.class)
public interface UserClient {

    /** Mirrors user-service {@code GET /users/user/{userId}}. */
    @GetMapping("/users/user/{userId}")
    UserSummary getUserById(@PathVariable("userId") String userId);

    /** Subset of the user-service response, only the deed-relevant fields. */
    record UserSummary(
            String id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String address
    ) {
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
