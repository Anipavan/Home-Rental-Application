package com.spa.home_rental_application.notification_service.notification_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fcm")
@Getter
@Setter
public class FcmProperties {
    private String serverKey;
}
