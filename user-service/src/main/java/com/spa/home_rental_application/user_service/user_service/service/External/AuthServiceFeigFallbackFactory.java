package com.spa.home_rental_application.user_service.user_service.service.External;

import com.spa.home_rental_application.user_service.user_service.DTO.Response.External.authResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Soft fallback for the Auth Service Feign client. When Auth Service is
 * down we return an empty role-mapping rather than failing the entire
 * caller request — the data is non-critical (used to enrich a list of
 * users by role) and an empty list degrades far more gracefully than a
 * 500.
 */
@Component
@Slf4j
public class AuthServiceFeigFallbackFactory implements FallbackFactory<AuthServiceFeig> {

    @Override
    public AuthServiceFeig create(Throwable cause) {
        return new AuthServiceFeig() {
            @Override
            public List<authResponseDto> getUserByRole(String roleName) {
                log.warn("Auth Service Feign fallback for role={} ({})",
                        roleName, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public authResponseDto getById(String id) {
                // Self-heal path is best-effort; on circuit-open we return
                // null so the caller falls through to its 404 branch.
                log.warn("Auth Service Feign fallback for getById={} ({})",
                        id, cause.getMessage());
                return null;
            }
        };
    }
}
