package com.spa.home_rental_application.user_service.user_service.service.External;

import com.spa.home_rental_application.user_service.user_service.DTO.Response.External.authResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client into Auth Service. Wrapped with a Resilience4j circuit
 * breaker named after the client itself ({@code auth-service}). When the
 * breaker is open we fall back via {@link AuthServiceFeigFallbackFactory}
 * so callers see a clean empty-result rather than a 5xx cascade.
 */
@FeignClient(
        name = "auth-service",
        fallbackFactory = AuthServiceFeigFallbackFactory.class)
public interface AuthServiceFeig {

    @GetMapping("/auth/role/{roleName}")
    List<authResponseDto> getUserByRole(@PathVariable("roleName") String roleName);
}
