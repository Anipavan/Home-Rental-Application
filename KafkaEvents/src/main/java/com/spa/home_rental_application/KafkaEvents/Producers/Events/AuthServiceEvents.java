package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.PasswordResetRequestedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLoginEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserLogoutEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;

/**
 * Producer interface for events originating from Auth Service.
 * Implemented by {@code AuthEventImpl} (in this same library).
 */
public interface AuthServiceEvents {
    void sendUserRegistered(UserRegisteredEvent event);
    void sendUserLogin(UserLoginEvent event);
    void sendUserLogout(UserLogoutEvent event);
    void sendPasswordResetRequested(PasswordResetRequestedEvent event);
}
