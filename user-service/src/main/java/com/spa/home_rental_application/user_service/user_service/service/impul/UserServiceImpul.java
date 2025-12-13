package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpul implements UserService {
    @Autowired
    UserRepo userRepo;
    @Override
    public User createUser(User user) {
        return userRepo.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    @Override
    public User getUserById(String userId) {
        return userRepo.findById(userId).orElse(null);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepo.findByEmail(email);
    }

    @Override
    public String deleteUserById(String userId) {
        userRepo.deleteById(userId);
        return "user Deleted";
    }

    @Override
    public User updateUser(User user) {
        return userRepo.save(user);
    }
}
