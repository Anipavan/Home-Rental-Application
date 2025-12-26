package com.spa.home_rental_application.user_service.user_service.mapper;

import com.spa.home_rental_application.user_service.user_service.DTO.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;

public class UserMapper {

    public static User toEntity(UserRequestDto dto) {
        return User.builder()
                .authUserId(dto.authUserId())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .email(dto.email())
                .phone(dto.phone())
                .dateOfBirth(dto.dateOfBirth())
                .gender(dto.gender())
                .address(dto.address())
                .profilePictureUrl(dto.profilePictureUrl())
                .idProofUrl(dto.idProofUrl())
                .build();
    }

    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getAuthUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAddress(),
                user.getProfilePictureUrl(),
                user.getIdProofUrl(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
