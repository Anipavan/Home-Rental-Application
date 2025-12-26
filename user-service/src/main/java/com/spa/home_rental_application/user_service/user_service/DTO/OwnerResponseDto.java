package com.spa.home_rental_application.user_service.user_service.DTO;

import java.time.LocalDateTime;

public record OwnerResponseDto(
        String id,
        String userId,
        String businessName,
        String gstNumber,
        String panNumber,
        String bankAccountNumber,
        String ifscCode,
        Integer totalProperties,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
