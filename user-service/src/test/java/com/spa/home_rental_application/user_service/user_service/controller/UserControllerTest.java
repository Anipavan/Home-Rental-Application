package com.spa.home_rental_application.user_service.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.ExceptionHandler.HandleException;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DuplicateUserException;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class,
        excludeAutoConfiguration = {
                org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration.class
        })
@Import(HandleException.class)
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserService userService;

    private UserResponseDto sampleResponse() {
        return new UserResponseDto("USR-1", "AUTH-1", "Alice", "Smith",
                "alice@example.com", "+919876543210",
                LocalDate.of(1995, 4, 12), "FEMALE", "Bangalore",
                null, null, LocalDateTime.now(), LocalDateTime.now(),
                // maritalStatus + tenantType (new optional fields)
                null, null,
                // kycStatus
                "PENDING");
    }

    @Test
    void createUser_returnsCreated() throws Exception {
        UserRequestDto req = new UserRequestDto("AUTH-1", "Alice", "Smith",
                "alice@example.com", "+919876543210",
                LocalDate.of(1995, 4, 12), "FEMALE", "Bangalore", null, null,
                // maritalStatus + tenantType
                null, null);
        when(userService.createUser(any())).thenReturn(sampleResponse());

        mvc.perform(post("/users/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("USR-1"));
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        String body = """
                { "authUserId":"AUTH-1", "firstName":"Alice", "email":"not-an-email" }
                """;
        mvc.perform(post("/users/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        UserRequestDto req = new UserRequestDto("AUTH-1", "Alice", "Smith",
                "alice@example.com", "+919876543210",
                LocalDate.of(1995, 4, 12), "FEMALE", "Bangalore", null, null,
                // maritalStatus + tenantType
                null, null);
        when(userService.createUser(any()))
                .thenThrow(new DuplicateUserException("email already in use: alice@example.com"));

        mvc.perform(post("/users/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USER"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        when(userService.getUserById("missing"))
                .thenThrow(new RecordNotFound("User not found with id: missing"));
        mvc.perform(get("/users/user/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECORD_NOT_FOUND"));
    }

    @Test
    void deleteUserById_returns200() throws Exception {
        when(userService.deleteUserById("USR-1")).thenReturn(sampleResponse());
        mvc.perform(delete("/users/USR-1"))
                .andExpect(status().isOk());
    }
}
