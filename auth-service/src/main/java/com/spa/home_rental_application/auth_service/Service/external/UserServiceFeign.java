package com.spa.home_rental_application.auth_service.Service.external;

import com.spa.home_rental_application.auth_service.Dto.External.UserProfileCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client into User Service. Resolves via Eureka — no hardcoded URL.
 *
 * Wrapped by a Resilience4j circuit breaker named after the client itself
 * ({@code HRA-user-service}) — see resilience4j config in application.yaml.
 * When the breaker is open or the call throws, control passes to
 * {@link UserServiceFeignFallbackFactory} which reports it as a transient
 * downstream failure rather than a generic 500.
 */
@FeignClient(
        name = "HRA-user-service",
        fallbackFactory = UserServiceFeignFallbackFactory.class)
public interface UserServiceFeign {

    @PostMapping("/users/user")
    Object createUser(@RequestBody UserProfileCreateRequest request);
}
