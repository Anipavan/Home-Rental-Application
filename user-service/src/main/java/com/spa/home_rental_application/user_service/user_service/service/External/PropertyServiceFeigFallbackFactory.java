package com.spa.home_rental_application.user_service.user_service.service.External;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Soft fallback for the Property Service Feign client. Returning an empty
 * tenant list keeps the wrapping owner-detail call alive — the consumer
 * gets fewer fields populated rather than a hard 503 cascading up the chain.
 */
@Component
@Slf4j
public class PropertyServiceFeigFallbackFactory implements FallbackFactory<PropertyServiceFeig> {

    @Override
    public PropertyServiceFeig create(Throwable cause) {
        return new PropertyServiceFeig() {
            @Override
            public List<String> getTenantIdsByOwner(String ownerId) {
                log.warn("Property Service Feign fallback for ownerId={} ({})",
                        ownerId, cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}
