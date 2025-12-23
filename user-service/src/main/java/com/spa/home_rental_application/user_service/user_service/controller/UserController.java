package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class UserController {
    @Autowired
    UserService userService;

    @PostMapping(
            value = "/user",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public User createUser(@RequestBody User user) {
        log.info("Request recieved for creating user.{}",user);
        return userService.createUser(user);
    }

    @GetMapping("/users")
    public List<User> getAllUsers(){

        return userService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable String userId)
    {
        return userService.getUserById(userId);
    }

    @GetMapping("/email/{email}")
    public User getUserByEmail(@PathVariable String email)
    {
        return userService.getUserByEmail(email);
    }

    @DeleteMapping("/{id}")
    public String deleteUserById(String userId){
        userService.deleteUserById(userId);
        return "deleted";
    }

    @PutMapping("/user/{id}")
    public User updateUser(User user){
       return userService.updateUser(user);
    }
}
