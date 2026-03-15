package com.spa.home_rental_application.auth_service.auth_service.DTO;

import com.spa.home_rental_application.auth_service.auth_service.DTO.UserResponseDTO;
import com.spa.home_rental_application.auth_service.auth_service.Entity.Role;
import com.spa.home_rental_application.auth_service.auth_service.Entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public UserMapper(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    public User toEntity(UserRequestDTO dto) {
        User entity = new User();
        entity.setUserName(dto.getFirstName().substring(0,3)+dto.getLastName().substring(0,3));
        if(dto.getUserPassword().equals(dto.getConfirmUserPassword())) {
            entity.setUserPassword(passwordEncoder.encode(dto.getUserPassword()));
            entity.setConfirmUserPassword(passwordEncoder.encode(dto.getUserPassword()));
            entity.setDateOfBirth(dto.getDateOfBirth());

        }
        entity.setUserCreatedDate(Instant.now());
        entity.setUserUpdatedDate(Instant.now());
        return entity;
    }

    public UserResponseDTO toResponseDTO(User entity) {
        return UserResponseDTO.builder()
                .user_id(entity.getUser_id())
                .userName(entity.getUsername())
                .roles(extractRoles(entity))
                .build();
    }

    public List<UserResponseDTO> toResponseDTOList(List<User> entities) {
        return entities.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    private List<String> extractRoles(User entity) {
        return entity.getRoles() != null
                ? entity.getRoles().stream().map(role -> role.getRoleName()).collect(Collectors.toList())
                : List.of();
    }
}
