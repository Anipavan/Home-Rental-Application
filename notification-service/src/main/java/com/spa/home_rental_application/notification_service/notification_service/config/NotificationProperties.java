package com.spa.home_rental_application.notification_service.notification_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
@Getter
@Setter
public class NotificationProperties {
    private boolean deliveryEnabled = true;
    private String fromEmail = "no-reply@homerental.local";
    private String fromName  = "Home Rental";
    private int maxRetries = 3;
    private int retryIntervalMinutes = 5;
}
