package com.spa.home_rental_application.auth_service.auth_service.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private boolean success;
    private String message;
    private UserAuthData data;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAuthData {
        private Long userId;
        private String username;
        private String email;
        private String token;
        private String refreshToken;
        private Long expiresIn;
        private String role;
    }
}
