package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.usersByRoleDto;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a user (publishes user.profile.created)")
    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto request) {
        log.info("POST /users/user email={}", request.email());
        UserResponseDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "List active users (paginated)")
    @GetMapping
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int pagenum,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        Pageable pageable = PageRequest.of(pagenum, size);
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @Operation(summary = "Get a user by id")
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(summary = "Get a user by email")
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @Operation(summary = "Get a user by their Auth Service id (cross-service join)")
    @GetMapping("/auth/{authUserId}")
    public ResponseEntity<UserResponseDto> getByAuthUserId(@PathVariable String authUserId) {
        return ResponseEntity.ok(userService.getUserByAuthUserId(authUserId));
    }

    @Operation(summary = "Soft-delete a user")
    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDto> deleteUserById(@PathVariable("id") String userId) {
        return ResponseEntity.ok(userService.deleteUserById(userId));
    }

    @Operation(summary = "Update a user (publishes user.profile.updated when fields change)")
    @PutMapping(value = "/user/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> updateUser(
            @Valid @RequestBody UserRequestDto userRequest,
            @PathVariable("id") String uid) {
        return ResponseEntity.ok(userService.updateUser(userRequest, uid));
    }

    @Operation(summary = "Search users by phone, email or first-name fragment")
    @GetMapping("/search/{searchParam}")
    public ResponseEntity<List<UserResponseDto>> searchUserByParam(@PathVariable("searchParam") String param) {
        return ResponseEntity.ok(userService.searchUserByParam(param));
    }

    @Operation(summary = "Upload a user document (PROFILE or ID_PROOF)")
    @PutMapping(value = "/{userId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDto> uploadUserDocument(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) throws IOException {
        return ResponseEntity.ok(userService.uploadUserDocument(userId, file, type));
    }

    @Operation(summary = "List users by role (joins on Auth Service via Feign)")
    @GetMapping("/role/{roleName}")
    public ResponseEntity<List<usersByRoleDto>> getUserByRole(@PathVariable String roleName) {
        log.info("GET /users/role/{}", roleName);
        return ResponseEntity.ok(userService.getUserByRole(roleName));
    }
}
