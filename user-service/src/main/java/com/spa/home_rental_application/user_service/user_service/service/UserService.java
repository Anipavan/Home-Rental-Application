package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;

import java.util.List;

public interface UserService {
    UserResponseDto createUser(UserRequestDto userRequest);
   List< UserResponseDto> getAllUsers();
    UserResponseDto getUserById(String userId);
    UserResponseDto getUserByEmail(String email);
    UserResponseDto deleteUserById(String userId);
    UserResponseDto updateUser(UserRequestDto userRequest,String userId);
    EmergencyContactResponseDto saveContact(EmergencyContactRequestDto emergencyContactsRequest);
    EmergencyContactResponseDto getContactByUserId(String userId);
}
