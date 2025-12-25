package com.spa.home_rental_application.user_service.user_service.DTO;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record UserRequestDto(
        @NotBlank(message = "authUserId is mandatory")
        String authUserId,

        @NotBlank(message = "firstName is mandatory")
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @NotBlank(message = "email is mandatory")
        @Email(message = "Invalid email format")
        String email,

        @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "Invalid phone number")
        String phone,

        LocalDate dateOfBirth,

        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "gender must be MALE, FEMALE or OTHER")
        String gender,

        @Size(max = 4000)
        String address,

        @Size(max = 1000)
        String profilePictureUrl,

        @Size(max = 1000)
        String idProofUrl
) {}