package com.spa.home_rental_application.auth_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.AuthServiceEvents.UserRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuthServiceEvents;
import com.spa.home_rental_application.auth_service.Config.JwtProperties;
import com.spa.home_rental_application.auth_service.Dto.External.UserProfileCreateRequest;
import com.spa.home_rental_application.auth_service.Dto.Request.RegisterRequest;
import com.spa.home_rental_application.auth_service.Dto.Response.RegisterResponse;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Exception.DuplicateUserException;
import com.spa.home_rental_application.auth_service.Repository.PasswordResetTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.RefreshTokenRepository;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.Impul.AuthServiceImpl;
import com.spa.home_rental_application.auth_service.Service.external.UserServiceFeign;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import com.spa.home_rental_application.auth_service.enums.Roles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JWTUtil jwtUtil;
    @Mock UserServiceFeign userServiceFeign;
    @Mock AuthServiceEvents authEvents;

    AuthServiceImpl service() {
        JwtProperties props = new JwtProperties();
        props.setSecret("U3VwZXJTZWNyZXRLZXlGb3JKV1RUb2tlbkdlbmVyYXRpb24xMjM0NTY3ODkwIQ==");
        return new AuthServiceImpl(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, authenticationManager, jwtUtil, props, userServiceFeign, authEvents,
                org.mockito.Mockito.mock(
                        com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher.class),
                15L);
    }

    @Test
    void register_persists_publishesEvent_andCallsUserService() {
        RegisterRequest req = new RegisterRequest(
                "asha.rao", "Strong123", Roles.TENANT, "asha@example.com",
                "Asha", "Rao", "FEMALE", "+919876543210", "1 MG Road",
                LocalDate.of(1995, 4, 12));
        when(userRepository.existsByUserName("asha.rao")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong123")).thenReturn("HASHED");
        when(userRepository.save(any(UserDetails.class))).thenAnswer(inv -> {
            UserDetails u = inv.getArgument(0);
            u.setId(101L);
            return u;
        });

        RegisterResponse resp = service().register(req);

        assertThat(resp.authUserId()).isEqualTo("101");
        assertThat(resp.role()).isEqualTo("TENANT");

        ArgumentCaptor<UserProfileCreateRequest> captor =
                ArgumentCaptor.forClass(UserProfileCreateRequest.class);
        verify(userServiceFeign).createUser(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("asha@example.com");
        // The profile DTO sent to User Service must NOT include the password field
        assertThat(captor.getValue().toString()).doesNotContain("Strong123");
        assertThat(captor.getValue().toString()).doesNotContain("HASHED");

        ArgumentCaptor<UserRegisteredEvent> evt = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(authEvents).sendUserRegistered(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("user.registered");
        assertThat(evt.getValue().getRole()).isEqualTo("TENANT");
    }

    @Test
    void register_whenUserNameExists_throwsDuplicate() {
        when(userRepository.existsByUserName("dup")).thenReturn(true);
        RegisterRequest req = new RegisterRequest("dup", "Strong123", Roles.OWNER,
                "x@y.com", "X", "Y", null, null, null, null);
        assertThatThrownBy(() -> service().register(req))
                .isInstanceOf(DuplicateUserException.class);
        verifyNoInteractions(userServiceFeign, authEvents);
    }

    @Test
    void register_whenEmailExists_throwsDuplicate() {
        when(userRepository.existsByUserName("u")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("x@y.com")).thenReturn(true);
        RegisterRequest req = new RegisterRequest("u", "Strong123", Roles.OWNER,
                "x@y.com", "X", "Y", null, null, null, null);
        assertThatThrownBy(() -> service().register(req))
                .isInstanceOf(DuplicateUserException.class);
    }
}
