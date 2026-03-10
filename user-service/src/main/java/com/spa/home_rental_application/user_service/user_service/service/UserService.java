package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface UserService {
    UserResponseDto createUser(UserRequestDto userRequest);
   Page< UserResponseDto> getAllUsers(Pageable pageable);
    UserResponseDto getUserById(String userId);
    UserResponseDto getUserByEmail(String email);
    UserResponseDto deleteUserById(String userId);
    UserResponseDto updateUser(UserRequestDto userRequest,String userId);
    List <UserResponseDto> searchUserByParam(String param);
    UserResponseDto uploadUserDocument(String userId, MultipartFile file, String type) throws IOException;
}
