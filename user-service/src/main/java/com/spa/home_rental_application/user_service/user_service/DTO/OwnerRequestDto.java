package com.spa.home_rental_application.user_service.user_service.DTO;

import jakarta.validation.constraints.*;

public record OwnerRequestDto(
        @NotBlank(message = "userId is mandatory")
        String userId,

        @NotBlank(message = "businessName is mandatory")
        String businessName,

        @Pattern(regexp = "^[0-9A-Z]{15}$", message = "Invalid GST number")
        String gstNumber,

        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN number")
        String panNumber,

        @Size(max = 30)
        String bankAccountNumber,

        @Size(max = 20)
        String ifscCode,

        @PositiveOrZero
        Integer totalProperties
) {}
