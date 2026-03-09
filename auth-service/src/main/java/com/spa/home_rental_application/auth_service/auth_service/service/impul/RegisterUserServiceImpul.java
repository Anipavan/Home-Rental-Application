package com.spa.home_rental_application.auth_service.auth_service.service.impul;

import com.spa.home_rental_application.auth_service.auth_service.DTO.RegisterRequest;
import com.spa.home_rental_application.auth_service.auth_service.entity.User;
import com.spa.home_rental_application.auth_service.auth_service.repository.UserRepository;
import com.spa.home_rental_application.auth_service.auth_service.service.RegisterUserService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RegisterUserServiceImpul implements RegisterUserService {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    public  RegisterUserServiceImpul(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.userRepository=userRepository;
        this.passwordEncoder=passwordEncoder;

    }
    @Override
    public User registerUser(RegisterRequest registerRequest) {
        User user= new User();
        String password=registerRequest.getPassword();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setPasswordHash( passwordEncoder.encode(password));

        return userRepository.save(user);
    }
}
