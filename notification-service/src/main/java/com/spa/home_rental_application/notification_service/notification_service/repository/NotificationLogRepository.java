package com.spa.home_rental_application.notification_service.notification_service.repository;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {
    Page<NotificationLog> findAll(Pageable pageable);
    List<NotificationLog> findByUserId(String userId);
    List<NotificationLog> findByStatusAndRetryCountLessThan(NotificationStatus status, int max);

    /**
     * All "unread" notifications for a user — anything that's been
     * dispatched but not yet flipped to READ. Used by the bulk
     * mark-all-as-read path so the bell badge clears in one round
     * trip when the user opens the dropdown.
     */
    List<NotificationLog> findByUserIdAndStatusIn(String userId, java.util.Collection<NotificationStatus> statuses);
}
