package com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO;

import lombok.*;

import java.time.LocalDateTime;
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public class APIErrorResponse {
        private LocalDateTime timestamp;
        private String message;
        private String errorCode;
}
