package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import com.spa.home_rental_application.property_service.property_service.service.PropertyImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@ResponseBody
public class PropertyController {
    private final PropertyImageService propertyImageService;

    public  PropertyController(PropertyImageService propertyImageService){
        this.propertyImageService = propertyImageService;
    }
    @PostMapping("/buildings/{id}/images")
    public ResponseEntity<String> uploadImage(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
        propertyImageService.uploadImage(id, file);
        return ResponseEntity.ok("Image uploaded successfully");
    }

    @GetMapping("/buildings/{id}/images")
    public List<PropertyImage> getImages(@PathVariable String id){
        return propertyImageService.getImages(id);
    }
}
