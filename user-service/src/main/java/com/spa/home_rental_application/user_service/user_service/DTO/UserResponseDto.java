package com.spa.home_rental_application.user_service.user_service.DTO;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponseDto(
        String id,
        String authUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String profilePictureUrl,
        String idProofUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
