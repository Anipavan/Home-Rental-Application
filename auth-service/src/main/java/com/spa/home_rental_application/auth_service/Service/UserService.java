package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Entity.UserDetails;

public interface UserService {
    UserDetails registerUser(UserDetails userRequest);
}