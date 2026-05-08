package com.spa.home_rental_application.lease_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
