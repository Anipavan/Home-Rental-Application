package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyContactRepo extends JpaRepository<EmergencyContact,String> {
    List<EmergencyContact> findByUserId(String userId);
}
