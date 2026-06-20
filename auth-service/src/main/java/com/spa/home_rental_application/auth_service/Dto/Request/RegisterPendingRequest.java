package com.spa.home_rental_application.auth_service.Dto.Request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Body for {@code POST /auth/register/pending} — the entry point for
 * the paid maintainer-registration flow. Same field set as
 * {@link RegisterRequest} <em>except</em> there's no {@code userRole}:
 * this endpoint is reserved for the "I'm a maintainer" signup card,
 * so the backend pins the role to TENANT (matching today's society-
 * signup behaviour where TENANT is later promoted to MAINTAINER via
 * the owner-approved membership-claim flow).
 *
 * <p>Validation rules are identical to {@link RegisterRequest} so a
 * user filling the same form on the frontend gets the same client-
 * side errors regardless of which signup card they pick.
 */
public record RegisterPendingRequest(

        @NotBlank(message = "userName is mandatory")
        @Size(min = 3, max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "userName may only contain letters, digits, '.', '_', '-'")
        String userName,

        @NotBlank(message = "password is mandatory")
        @Size(min = 8, max = 100, message = "password must be 8–100 characters")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$",
                 message = "password must contain at least one uppercase, one lowercase and one digit")
        String userPassword,

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

        @Pattern(regexp = "SINGLE|MARRIED|DIVORCED|WIDOWED",
                 message = "maritalStatus must be SINGLE, MARRIED, DIVORCED or WIDOWED")
        String maritalStatus,

        @Pattern(regexp = "BACHELOR|FAMILY",
                 message = "tenantType must be BACHELOR or FAMILY")
        String tenantType
) {}
