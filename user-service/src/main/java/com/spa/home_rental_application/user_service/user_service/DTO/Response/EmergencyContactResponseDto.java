package com.spa.home_rental_application.user_service.user_service.DTO.Response;

import java.time.LocalDateTime;

public record EmergencyContactResponseDto(
        String id,
        String userId,
        String name,
        String relation,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
