package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContacts;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.EmergencyContactMapper;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import com.spa.home_rental_application.user_service.user_service.utils.events.UserProfileCreatedEvent;
import com.spa.home_rental_application.user_service.user_service.utils.events.UserProfileUpdatedEvent;
import com.spa.home_rental_application.user_service.user_service.utils.userEventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpul implements UserService {
    private final UserRepo userRepo;
    private final EmergencyContactRepo econtactRepo;
    private final userEventProducer eventProducer;
    public  UserServiceImpul(UserRepo userRepo, EmergencyContactRepo econtactRepo, userEventProducer eventProducer){
        this.userRepo=userRepo;
        this.econtactRepo=econtactRepo;
        this.eventProducer=eventProducer;
    }

    @Override
    public UserResponseDto createUser(UserRequestDto userRequest) {
        User user = UserMapper.toEntity(userRequest);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepo.save(user);
        eventProducer.sendUserProfileCreated(UserProfileCreatedEvent.builder().eventType("User-Profile-Created")

                .role("user")
                .userId(saved.getId())
                .timestamp(Instant.from(saved.getCreatedAt()))
                .build()
        );
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

        eventProducer.sendUserProfileUpdated(UserProfileUpdatedEvent.builder()
                        .changes("changed user profile")
                        .userId(userSaved.getId())
                        .eventType("User-Updated-Event")
                        .timestamp(Instant.from(userSaved.getUpdatedAt()))
                .build());
        return UserMapper.toDto(userSaved);
    }

    @Override
    public EmergencyContactResponseDto saveContact(EmergencyContactRequestDto emergencyContactsRequest) {

        EmergencyContacts contact= EmergencyContactMapper.toEntity(emergencyContactsRequest);
        contact.setCreatedAt(LocalDateTime.now());
        contact.setUpdatedAt(LocalDateTime.now());
        return  EmergencyContactMapper.toDto(econtactRepo.save(contact));
    }

    @Override
    public EmergencyContactResponseDto getContactByUserId(String userId) {
        EmergencyContacts contact=econtactRepo.findByUserId(userId);
        if(contact==null)
        {
            throw new RecordNotFound("Contact with given userId is not prest : "+userId);
        }
        return EmergencyContactMapper.toDto(contact);
    }
}