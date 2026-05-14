package com.spa.home_rental_application.user_service.user_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body the SPA posts when a user adds or updates their bank account
 * from the Profile → Bank details section. All sensitive fields are
 * required except the optional {@code branch} and {@code upiId}.
 *
 * <p>Validation rules mirror the Indian banking format the platform
 * targets — account numbers run 9-18 digits, IFSC is a fixed 11-char
 * code with a {@code 0} in position 5 (e.g. {@code SBIN0001234}).
 */
public record BankAccountRequestDto(
        @NotBlank(message = "accountHolderName is mandatory")
        @Size(max = 120)
        String accountHolderName,

        @NotBlank(message = "bankName is mandatory")
        @Size(max = 120)
        String bankName,

        @NotBlank(message = "accountNumber is mandatory")
        @Pattern(regexp = "\\d{9,18}", message = "accountNumber must be 9-18 digits")
        String accountNumber,

        @NotBlank(message = "ifscCode is mandatory")
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "ifscCode must be 11 characters in the format ABCD0XXXXXX")
        String ifscCode,

        @Size(max = 200) String branch,

        /** SAVINGS | CURRENT — defaults to SAVINGS server-side when blank. */
        @Size(max = 20) String accountType,

        @Size(max = 100) String upiId
) {}
