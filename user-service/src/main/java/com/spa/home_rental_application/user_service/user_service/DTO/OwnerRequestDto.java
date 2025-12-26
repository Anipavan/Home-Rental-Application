package com.spa.home_rental_application.user_service.user_service.DTO;

import jakarta.validation.constraints.*;

public record OwnerRequestDto(
        @NotBlank(message = "userId is mandatory")
        String userId,

        @NotBlank(message = "businessName is mandatory")
        String businessName,

        @NotBlank(message = "gstNumber is mandatory")
        @Pattern(regexp = "^[0-9A-Z]{15}$", message = "Invalid GST number")
        String gstNumber,
        @NotBlank(message = "panNumber is mandatory")
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number")
        String panNumber,

        @Size(max = 30)
        @NotBlank(message = "bankAccountNumber is mandatory")
        String bankAccountNumber,

        @Size(max = 20)
        @NotBlank(message = "ifscCode is mandatory")
        @Pattern(regexp = "^[A-Z]{4}0[0-9A-Z]{6}$",message = "Invalid ifscCode")
        String ifscCode,

        @PositiveOrZero
        @Max(10)
        Integer totalProperties
) {}
