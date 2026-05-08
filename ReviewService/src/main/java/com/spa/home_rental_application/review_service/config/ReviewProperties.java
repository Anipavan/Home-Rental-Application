package com.spa.home_rental_application.review_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reviews")
@Getter
@Setter
public class ReviewProperties {
    /**
     * When true (default), reviews are auto-approved on submission. Flip to
     * false in production to require admin moderation before they go public.
     */
    private boolean autoPublish = true;
}
