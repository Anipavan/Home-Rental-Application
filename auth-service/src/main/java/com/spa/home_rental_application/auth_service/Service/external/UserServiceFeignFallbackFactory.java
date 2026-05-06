package com.spa.home_rental_application.auth_service.Service.external;

import com.spa.home_rental_application.auth_service.Dto.External.UserProfileCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fallback factory invoked by Resilience4j when the {@link UserServiceFeign}
 * call fails, times out, or the circuit is open.
 *
 * We rethrow as 503 so the surrounding transactional logic in AuthServiceImpl
 * can roll back the just-created Auth row instead of leaving an orphaned
 * credentials record. Using a {@link FallbackFactory} (instead of a plain
 * {@code fallback}) lets us log the *cause* — useful for distinguishing
 * "User Service is down" from "User Service rejected the payload".
 */
@Component
@Slf4j
public class UserServiceFeignFallbackFactory implements FallbackFactory<UserServiceFeign> {

    @Override
    public UserServiceFeign create(Throwable cause) {
        return new UserServiceFeign() {
            @Override
            public Object createUser(UserProfileCreateRequest request) {
                log.error("User Service Feign call failed (circuit open or downstream error): {}",
                        cause.getMessage(), cause);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "User Service is temporarily unavailable. Please retry shortly.",
                        cause);
            }
        };
    }
}
