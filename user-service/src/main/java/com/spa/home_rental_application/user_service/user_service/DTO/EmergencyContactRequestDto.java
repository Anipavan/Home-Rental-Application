package com.spa.home_rental_application.user_service.user_service.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequestDto(

        @NotBlank(message = "userId is mandatory")
        String userId,

        @NotBlank(message = "name is mandatory")
        @Size(max = 100)
        String name,

        @Size(max = 50)
        String relation,

        @NotBlank(message = "phone is mandatory")
        @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "Invalid phone number")
        String phone
) {}
