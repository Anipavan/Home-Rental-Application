package com.spa.home_rental_application.auth_service.auth_service.service;

import com.spa.home_rental_application.auth_service.auth_service.DTO.AuthResponse;
import com.spa.home_rental_application.auth_service.auth_service.DTO.RegisterRequest;
import com.spa.home_rental_application.auth_service.auth_service.entity.User;

public interface RegisterUserService {

    User registerUser(RegisterRequest registerRequest);
}
