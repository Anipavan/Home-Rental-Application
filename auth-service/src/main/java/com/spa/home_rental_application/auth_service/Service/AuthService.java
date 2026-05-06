package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Dto.Request.*;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.AuthUserResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.enums.Roles;

import java.util.List;

public interface AuthService {
    RegisterResponse register(RegisterRequest req);
    AuthResponse     login(LoginRequest req, String ipAddress, String userAgent);
    AuthResponse     refresh(RefreshTokenRequest req);
    void             logout(LogoutRequest req);
    void             startPasswordReset(ForgotPasswordRequest req);
    void             completePasswordReset(ResetPasswordRequest req);
    List<AuthUserResponse> getUsersByRole(Roles role);
    AuthUserResponse getById(Long id);
}
