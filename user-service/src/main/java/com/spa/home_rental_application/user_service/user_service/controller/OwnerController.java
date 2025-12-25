package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class OwnerController {

    private final OwnerService ownerService;
    public  OwnerController(OwnerService ownerService){
        this.ownerService=ownerService;
    }

    @PostMapping(value = "/owners", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnerResponseDto>  createOwner(@Valid  @RequestBody OwnerRequestDto ownerRequest) {
        log.info("Request recieved for creating owner.{}",ownerRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(ownerService.createOwner(ownerRequest));
    }

    @GetMapping("/owners")
    public List<OwnerResponseDto> getAllOwners(){
        return ownerService.getAllOwners();
    }
    @GetMapping("/owners/{ownerId}")
    public ResponseEntity<OwnerResponseDto> getOwnerById(@PathVariable String ownerId){
        return ResponseEntity.status(HttpStatus.FOUND).body(ownerService.getOwnerById(ownerId));
    }
    @GetMapping("/owners/{ownerId}/tenants")
    public ResponseEntity<List<UserResponseDto>> getTenentsByOwnerId(@PathVariable String ownerId){
        return ResponseEntity.ok().body(ownerService.getTenentsByOwnerId(ownerId));
    }

    @PutMapping("/owners/{ownerId}")
    public ResponseEntity<OwnerResponseDto> updateOwner(@PathVariable String ownerId,@Valid @RequestBody OwnerRequestDto ownerRequest)
    {
        return ResponseEntity.ok().body(ownerService.updateOwner(ownerId,ownerRequest));
    }

}
