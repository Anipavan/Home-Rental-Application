package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContacts;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class ContactController {


    private final UserService userService;
    public  ContactController(UserService userService){
        this.userService=userService;
    }

    @PostMapping(value = "/savecontact",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergencyContactResponseDto> saveContact(@Valid @RequestBody EmergencyContactRequestDto emergencyContactsRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.saveContact(emergencyContactsRequest));
    }

    @GetMapping(value = "/getContcat/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergencyContactResponseDto> getContactByUserId(@PathVariable("id") String userId) {
        return ResponseEntity.ok().body(userService.getContactByUserId(userId));
    }
}
