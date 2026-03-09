package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/maintenance",produces = MediaType.APPLICATION_JSON_VALUE)
public class RequestActions {

    private RequestService requestService;
    public  RequestActions(RequestService requestService){
        this.requestService=requestService;
    }

    @PostMapping(value = "/requests/{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequest> assignToTechnician(@PathVariable String requestId, @RequestBody MaintenanceRequest request)
    {
        MaintenanceRequest assigned=requestService.assignToTechnician(requestId,request);
        return ResponseEntity.ok(assigned);
    }
}
