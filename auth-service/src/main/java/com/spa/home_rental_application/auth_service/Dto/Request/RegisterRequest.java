package com.spa.home_rental_application.auth_service.Dto.Request;

import com.spa.home_rental_application.auth_service.enums.Roles;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Body for POST /auth/register. Carries credentials + the profile fields
 * that Auth Service forwards to User Service to create a linked profile
 * row. Note: the password is mandatory here but never echoed back in any
 * response DTO.
 */
public record RegisterRequest(

        @NotBlank(message = "userName is mandatory")
        @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "userName may only contain letters, digits, '.', '_', '-'")
        String userName,

        @NotBlank(message = "password is mandatory")
        @Size(min = 8, max = 100, message = "password must be 8–100 characters")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$",
                 message = "password must contain at least one uppercase, one lowercase and one digit")
        String userPassword,

        @NotNull(message = "role is mandatory")
        Roles userRole,

        @NotBlank(message = "email is mandatory")
        @Email(message = "invalid email format")
        String email,

        @NotBlank(message = "firstName is mandatory")
        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "gender must be MALE, FEMALE or OTHER")
        String gender,

        @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "invalid phone number")
        String phone,

        @Size(max = 4000)
        String address,

        LocalDate dateOfBirth,

        /**
         * Optional marital status. Persisted on the user-service profile.
         * Used for tenant-search filtering by some landlords (e.g.
         * "family-only" listings). Pattern keeps it server-controlled —
         * a corrupted enum on the wire is a 400, never silently stored.
         */
        @Pattern(regexp = "SINGLE|MARRIED|DIVORCED|WIDOWED",
                 message = "maritalStatus must be SINGLE, MARRIED, DIVORCED or WIDOWED")
        String maritalStatus,

        /**
         * Optional tenant category. {@code BACHELOR} = unmarried tenant,
         * typically interested in shared/PG accommodation. {@code FAMILY}
         * = looking for whole-flat tenancy. Landlords filter on this
         * field when restricting their listings (the "bachelor not
         * allowed" / "family only" toggle in India rental ads). Only
         * meaningful for TENANT role — owners can submit it but it's
         * ignored downstream.
         */
        @Pattern(regexp = "BACHELOR|FAMILY",
                 message = "tenantType must be BACHELOR or FAMILY")
        String tenantType
) {}
