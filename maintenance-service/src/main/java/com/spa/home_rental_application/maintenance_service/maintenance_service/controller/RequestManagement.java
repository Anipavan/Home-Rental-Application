package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/maintenance",produces = MediaType.APPLICATION_JSON_VALUE)
public class RequestManagement {
    private RequestService requestService;
public  RequestManagement(RequestService requestService){
    this.requestService=requestService;
}
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequest> createRequest(@RequestBody MaintenanceRequest request)

    {
        MaintenanceRequest created=requestService.createRequest(request);

        return ResponseEntity.ok(created);
    }
    @GetMapping(value = "/requests",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MaintenanceRequest>> getAllRequests()

    {
        List<MaintenanceRequest> allRequests=requestService.getAllRequests();

        return ResponseEntity.ok(allRequests);
    }
    @GetMapping(value = "/requests/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Optional<MaintenanceRequest>> getRequestsById(@PathVariable("id") String requestId)
    {
        Optional<MaintenanceRequest> request=requestService.getRequestsById(requestId);

        return ResponseEntity.ok(request);
    }

    @PutMapping(value = "/requests/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequest> updateRequest(@PathVariable String id,@RequestBody MaintenanceRequest request)
    {
        MaintenanceRequest updatedRequest=requestService.updateRequest(id,request);

        return ResponseEntity.ok(updatedRequest);
    }
    @DeleteMapping(value = "/requests/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequest> deleteRequest(@PathVariable String id)
    {
        MaintenanceRequest deletedRequest=requestService.deleteRequest(id);

        return ResponseEntity.ok(deletedRequest);
    }

    @GetMapping(value = "/requests/status/{status}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MaintenanceRequest>> getRequestsByStatus(@PathVariable String status)
    {
        List<MaintenanceRequest> requests=requestService.getRequestsByStatus(status);

        return ResponseEntity.ok(requests);
    }
    @GetMapping(value = "/requests/priority/{priority}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MaintenanceRequest>> getRequestsByPriority(@PathVariable String priority)
    {
        List<MaintenanceRequest> requests=requestService.getRequestsByPriority(priority);

        return ResponseEntity.ok(requests);
    }
}
