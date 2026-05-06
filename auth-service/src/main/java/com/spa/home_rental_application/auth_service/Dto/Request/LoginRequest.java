package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "userName is mandatory") String userName,
        @NotBlank(message = "password is mandatory") String password
) {}
