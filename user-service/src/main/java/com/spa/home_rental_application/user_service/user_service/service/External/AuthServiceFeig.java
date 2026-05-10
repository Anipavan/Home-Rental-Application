package com.spa.home_rental_application.user_service.user_service.service.External;

import com.spa.home_rental_application.user_service.user_service.DTO.Response.External.authResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client into Auth Service. Wrapped with a Resilience4j circuit
 * breaker named after the client itself ({@code HRA-auth-service}). When
 * the breaker is open we fall back via {@link AuthServiceFeigFallbackFactory}
 * so callers see a clean empty-result rather than a 5xx cascade.
 *
 * <p><b>Service name:</b> auth-service registers itself with Eureka as
 * {@code HRA-auth-service} (matches {@code spring.application.name} in
 * its yaml). The previous {@code name = "auth-service"} value never
 * resolved → every call tripped the fallback, which silently returned
 * an empty list — so the owner-side "Assign tenant" dropdown read
 * empty even though tenants existed in the auth DB.
 */
@FeignClient(
        name = "HRA-auth-service",
        fallbackFactory = AuthServiceFeigFallbackFactory.class)
public interface AuthServiceFeig {

    @GetMapping("/auth/role/{roleName}")
    List<authResponseDto> getUserByRole(@PathVariable("roleName") String roleName);

    /**
     * Fetch a single AuthUser by id. Used by the User Service self-heal
     * path: when {@code GET /users/auth/{authUserId}} fires for a user that
     * has no profile row yet, we Feign-call this to recover the auth-tier
     * identity and create a stub User on the spot.
     *
     * <p>Hits the OWNER+ADMIN-accessible {@code /auth/users/lookup/{id}}
     * endpoint so the caller's JWT (always an authenticated user — owner
     * or admin) is sufficient.
     */
    @GetMapping("/auth/users/lookup/{id}")
    authResponseDto getById(@PathVariable("id") String id);
}
