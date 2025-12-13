package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.Entities.User;

import java.util.List;

public interface UserService {
    User createUser(User user);
    List<User>getAllUsers();
    User getUserById(String userId);
    User getUserByEmail(String email);
    String deleteUserById(String userId);
    User updateUser(User user);
}
