package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContacts;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepo extends JpaRepository<EmergencyContacts,String> {
    EmergencyContacts findByUserId(String userId);
}
