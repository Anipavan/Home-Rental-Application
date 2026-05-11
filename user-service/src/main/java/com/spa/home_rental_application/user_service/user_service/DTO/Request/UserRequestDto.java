package com.spa.home_rental_application.user_service.user_service.DTO.Request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record UserRequestDto(

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

        // Keep this list in sync with the frontend EditForm dropdown
        // (frontend/src/pages/tenant/profile.tsx). PREFER_NOT_TO_SAY was
        // missing from the regex while the UI offered it, causing every
        // profile update — including the profile-picture flow which
        // re-PUTs the full user — to 400 on validation for any user who
        // had selected that option. Empty string is also accepted so
        // we don't trip when a form serializes the unselected case.
        @Pattern(regexp = "^$|MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY",
                message = "gender must be MALE, FEMALE, OTHER, or PREFER_NOT_TO_SAY")
        String gender,

        @Size(max = 4000)
        String address,

        @Size(max = 10000)
        String profilePictureUrl,

        @Size(max = 10000)
        String idProofUrl
) {}