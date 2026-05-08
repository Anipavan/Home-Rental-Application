package com.spa.home_rental_application.document_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreSignedUrlDto(
        String documentId,
        String url,
        LocalDateTime expiresAt
) {
}
