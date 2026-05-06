package com.spa.home_rental_application.notification_service.notification_service.repository;

import com.spa.home_rental_application.notification_service.notification_service.entities.UserNotificationPreference;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserNotificationPreferenceRepository extends MongoRepository<UserNotificationPreference, String> {
    Optional<UserNotificationPreference> findByUserId(String userId);
}
