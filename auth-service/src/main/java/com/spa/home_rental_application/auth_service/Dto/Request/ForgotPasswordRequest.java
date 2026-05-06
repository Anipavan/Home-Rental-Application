package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "email is mandatory")
        @Email(message = "invalid email format")
        String email
) {}
