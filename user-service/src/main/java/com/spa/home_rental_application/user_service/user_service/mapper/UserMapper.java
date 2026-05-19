package com.spa.home_rental_application.user_service.user_service.mapper;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
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
                // Empty-string-to-null so an unfilled dropdown doesn't
                // get persisted as "" (which then defeats `is null` /
                // `isPresent()` checks downstream).
                .maritalStatus(nullIfBlank(dto.maritalStatus()))
                .tenantType(nullIfBlank(dto.tenantType()))
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
                user.getUpdatedAt(),
                user.getMaritalStatus(),
                user.getTenantType(),
                user.getKycStatus()
        );
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
