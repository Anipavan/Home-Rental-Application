package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class UserController {

    private  final UserService userService;

    public UserController(UserService userService)
    {
        this.userService=userService;
    }

    @PostMapping(
            value = "/user",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDto> createUser(@Valid  @RequestBody UserRequestDto request) {
        log.info("Request recieved for creating user.{}",request);
        UserResponseDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("")
    public ResponseEntity<List<UserResponseDto>> getAllUsers(){
        return ResponseEntity.ok().body( userService.getAllUsers());
    }

    @GetMapping("/user/{userId}")
    public  ResponseEntity<UserResponseDto>  getUserById(@PathVariable String userId)
    {
        return ResponseEntity.ok().body(userService.getUserById(userId));
    }

    @GetMapping("/email/{email}")
    public   ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email)
    {
        return ResponseEntity.ok().body(userService.getUserByEmail(email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDto> deleteUserById(@PathVariable("id") String userId){
        return ResponseEntity.ok().body( userService.deleteUserById(userId));
    }

    @PutMapping("/user/{id}")
    public ResponseEntity<UserResponseDto> updateUser(@Valid @RequestBody UserRequestDto userRequest,@PathVariable("id") String uid){

        return ResponseEntity.ok().body(userService.updateUser(userRequest,uid));
    }
}
