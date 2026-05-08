package com.spa.home_rental_application.kyc_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record DigilockerLinkRequest(
        @NotBlank String userId,
        @NotBlank String authCode,
        String redirectUri
) {
}
