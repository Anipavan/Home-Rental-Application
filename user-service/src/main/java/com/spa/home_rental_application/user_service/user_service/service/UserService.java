package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;

import java.util.List;

public interface UserService {
    UserResponseDto createUser(UserRequestDto userRequest);
   List< UserResponseDto> getAllUsers();
    UserResponseDto getUserById(String userId);
    UserResponseDto getUserByEmail(String email);
    UserResponseDto deleteUserById(String userId);
    UserResponseDto updateUser(UserRequestDto userRequest,String userId);
}
