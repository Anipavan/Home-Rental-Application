package com.spa.home_rental_application.auth_service.auth_service.Controller;

import com.spa.home_rental_application.auth_service.auth_service.DTO.UserRequestDTO;
import com.spa.home_rental_application.auth_service.auth_service.DTO.UserResponseDTO;
import com.spa.home_rental_application.auth_service.auth_service.Service.userService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    userService service;
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@RequestBody @Valid UserRequestDTO userRequestDTO)
    {
        return ResponseEntity.ok().body(service.registerUser(userRequestDTO));
    }
}
