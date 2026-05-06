package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token is mandatory") String token,

        @NotBlank(message = "newPassword is mandatory")
        @Size(min = 8, max = 100, message = "password must be 8–100 characters")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$",
                 message = "password must contain at least one uppercase, one lowercase and one digit")
        String newPassword
) {}
