package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class OwnerController {

    @Autowired
    OwnerService ownerService;

    @PostMapping(value = "/owners", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Owners createOwner(@RequestBody Owners owner) {
        log.info("Request recieved for creating owner.{}",owner);
        return ownerService.createOwner(owner);
    }

    @GetMapping("/owners")
    public List<Owners> getAllOwners(){
        return ownerService.getAllOwners();
    }
    @GetMapping("/owners/{ownerId}")
    public Owners getOwnerById(@PathVariable String ownerId){
        return ownerService.getOwnerById(ownerId);
    }
    @GetMapping("/owners/{ownerId}/tenants")
    public List<User> getTenentsByOwnerId(@PathVariable String ownerId){
        return ownerService.getTenentsByOwnerId(ownerId);
    }

    @PutMapping("/owners/{ownerId}")
    public Owners updateOwner(@PathVariable String ownerId,Owners owner)
    {
        return ownerService.updateOwner(ownerId,owner);
    }
}
