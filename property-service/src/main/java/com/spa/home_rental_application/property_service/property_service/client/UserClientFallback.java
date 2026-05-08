package com.spa.home_rental_application.property_service.property_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback that returns an empty {@link UserClient.UserSummary} when
 * user-service is unreachable. The deed renderer treats null/empty fields
 * as "fall back to handwritten blank", so the PDF still produces.
 */
@Component
@Slf4j
public class UserClientFallback implements UserClient {

    @Override
    public UserSummary getUserById(String userId) {
        log.warn("user-service unavailable — falling back to empty user summary for userId={}",
                userId);
        return UserSummary.empty();
    }
}
