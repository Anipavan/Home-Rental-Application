package com.spa.home_rental_application.property_service.property_service.ExceptionClass.DTO;

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
