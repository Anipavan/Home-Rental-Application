package com.spa.home_rental_application.auth_service.auth_service.controller;

import com.spa.home_rental_application.auth_service.auth_service.DTO.AuthResponse;
import com.spa.home_rental_application.auth_service.auth_service.DTO.RegisterRequest;
import com.spa.home_rental_application.auth_service.auth_service.entity.User;
import com.spa.home_rental_application.auth_service.auth_service.service.RegisterUserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth")
public class Authcontroller {
    private RegisterUserService registerUserService;
   public Authcontroller(RegisterUserService registerUserService){
       this.registerUserService=registerUserService;
   }


    @PostMapping(value = "/register",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> registerUser(@RequestBody @Valid RegisterRequest registerRequest){
        User registeredUser=registerUserService.registerUser(registerRequest);
        return ResponseEntity.ok(registeredUser);
    }
}
