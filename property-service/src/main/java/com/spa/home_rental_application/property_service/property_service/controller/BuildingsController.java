package com.spa.home_rental_application.property_service.property_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/properties")
public class BuildingsController {

    @GetMapping("/buildings")
    public String greet() {
        return "hey inside building";
    }
}
