package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.EmergencyContactMapper;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class UserServiceImpul implements UserService {
    private final UserRepo userRepo;
    private final EmergencyContactRepo econtactRepo;
    private final UserServiceEvents userServiceEvent;
    public  UserServiceImpul(UserRepo userRepo, EmergencyContactRepo econtactRepo, UserServiceEvents userServiceEvents){
        this.userRepo=userRepo;
        this.econtactRepo=econtactRepo;
        this.userServiceEvent = userServiceEvents;
    }

    @Override
    public UserResponseDto createUser(UserRequestDto userRequest) {
        User user = UserMapper.toEntity(userRequest);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepo.save(user);

        userServiceEvent.sendUserProfileCreated(UserProfileCreatedEvent.builder()
                .eventType("User-Profile Created").userId(saved.getId())
                .role("roles").timestamp(LocalDateTime.now()).build());
        return UserMapper.toDto(saved);
    }

    @Override
    public Page<UserResponseDto> getAllUsers(Pageable pageable) {

        Page<UserResponseDto> allusers= userRepo.findAll(pageable).map(UserMapper::toDto);
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
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RecordNotFound("User with the given email is not present: " + email);
        }
        return UserMapper.toDto(user);
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

        userServiceEvent.sendUserProfileUpdated(UserProfileUpdatedEvent.builder()
                        .changes("Changed user data")
                        .eventType("UserUpdatedEvent")
                        .userId(userSaved.getId())
                        .timestamp(Instant.now())
                .build());
        return UserMapper.toDto(userSaved);
    }
}