package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank(message = "userId is mandatory") String userId,
        @NotBlank(message = "comment is mandatory")
        @Size(max = 2000)
        String comment
) {}
