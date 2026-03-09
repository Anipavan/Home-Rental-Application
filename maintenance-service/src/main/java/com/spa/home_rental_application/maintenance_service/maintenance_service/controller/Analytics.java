package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/maintenance", produces = MediaType.APPLICATION_JSON_VALUE)

public class Analytics {
    private  RequestService requestService;
    public Analytics(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/stats/category/{category}")
    public ResponseEntity<List<MaintenanceRequest>> getRequestByCategory(@PathVariable String category)
    {
        return  ResponseEntity.ok(requestService.getRequestByCategory(category));
    }
    @GetMapping("/stats/pending")
    public ResponseEntity<Integer> getPendingRequestCount()
    {
        return  ResponseEntity.ok(requestService.getPendingRequestCount());
    }
}