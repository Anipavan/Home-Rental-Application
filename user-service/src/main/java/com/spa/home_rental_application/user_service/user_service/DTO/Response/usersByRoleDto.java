package com.spa.home_rental_application.user_service.user_service.DTO.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record usersByRoleDto(String firstName,
                             String lastName,
                             String email,
                             String phone,
                             LocalDate dateOfBirth,
                             String gender,
                             String address,
                             String userName,
                             String role) {}
