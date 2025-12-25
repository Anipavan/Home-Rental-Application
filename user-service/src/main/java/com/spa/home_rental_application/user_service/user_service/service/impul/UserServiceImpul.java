package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.DTO.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpul implements UserService {
    @Autowired
    UserRepo userRepo;
    @Override
    public UserResponseDto createUser(UserRequestDto userRequest) {
        User user = UserMapper.toEntity(userRequest);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepo.save(user);
        return UserMapper.toDto(saved);
    }

    @Override
    public List<UserResponseDto> getAllUsers() {

       List<UserResponseDto> allusers= userRepo.findAll().stream()
               .map(user->UserMapper.toDto(user))
               .collect(Collectors.toList());
       return allusers;
    }

    @Override
    public UserResponseDto getUserById(String userId) {

        User user= userRepo.findById(userId).orElseThrow(()->new RecordNotFound
                ("User with the given Id is not present :"+userId));
       return UserMapper.toDto(user);
    }

    @Override
    public UserResponseDto getUserByEmail(String email) {
        return UserMapper.toDto(userRepo.findByEmail(email));
    }

    @Override
    public UserResponseDto deleteUserById(String userId) {
        User user= userRepo.findById(userId).orElseThrow(()->new RecordNotFound
                ("User with the given Id is not present :"+userId));
        userRepo.delete(user);
        return UserMapper.toDto(user);
    }

    @Override
    public UserResponseDto updateUser(UserRequestDto userRequest, String userId) {
        User userRequestEntity = UserMapper.toEntity(userRequest);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RecordNotFound("User with the given id is not present. " + userId));


        if (userRequestEntity.getFirstName() != null && !userRequestEntity.getFirstName().isBlank()) {
            user.setFirstName(userRequestEntity.getFirstName());
        }

        if (userRequestEntity.getLastName() != null && !userRequestEntity.getLastName().isBlank()) {
            user.setLastName(userRequestEntity.getLastName());
        }

        if (userRequestEntity.getEmail() != null && !userRequestEntity.getEmail().isBlank()) {
            user.setEmail(userRequestEntity.getEmail());
        }

        if (userRequestEntity.getPhone() != null && !userRequestEntity.getPhone().isBlank()) {
            user.setPhone(userRequestEntity.getPhone());
        }

        if (userRequestEntity.getDateOfBirth() != null) {
            user.setDateOfBirth(userRequestEntity.getDateOfBirth());
        }

        if (userRequestEntity.getGender() != null && !userRequestEntity.getGender().isBlank()) {
            user.setGender(userRequestEntity.getGender());
        }

        if (userRequestEntity.getAddress() != null && !userRequestEntity.getAddress().isBlank()) {
            user.setAddress(userRequestEntity.getAddress());
        }

        if (userRequestEntity.getProfilePictureUrl() != null && !userRequestEntity.getProfilePictureUrl().isBlank()) {
            user.setProfilePictureUrl(userRequestEntity.getProfilePictureUrl());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User userSaved = userRepo.save(user);
        return UserMapper.toDto(userSaved);
    }
}