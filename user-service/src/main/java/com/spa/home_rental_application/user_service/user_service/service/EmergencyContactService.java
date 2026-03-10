package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmergencyContactService {
    EmergencyContactResponseDto saveUsersEmergencyContact(EmergencyContactRequestDto emergencyContactRequestDto);
    EmergencyContactResponseDto UpdateEmergencyContact(EmergencyContactRequestDto emergencyContactRequestDto, String contactId);
    void DeleteEmergencyContact(String contactId);
    Page<EmergencyContactResponseDto> getAllContacts(Pageable pageable);
    List<EmergencyContactResponseDto> getAllContactsByUserId(String userId);
}
