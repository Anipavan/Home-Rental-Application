package com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error envelope for all REST endpoints in this service.
 * Field-level validation errors are surfaced via {@link #fieldErrors}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIErrorResponse {
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private String errorCode;
    private String path;
    private List<Map<String, String>> fieldErrors;
}
