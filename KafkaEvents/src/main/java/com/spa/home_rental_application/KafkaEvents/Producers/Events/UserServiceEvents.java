package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;

public interface UserServiceEvents {
    void sendUserProfileCreated(UserProfileCreatedEvent event);
    void sendUserProfileUpdated(UserProfileUpdatedEvent event);
    void sendOwnerRegistered(OwnerRegisteredEvent event);
}
