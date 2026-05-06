package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AddCommentRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AssignTechnicianRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.StatusChangeRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/maintenance", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@Tag(name = "Maintenance Actions", description = "Assign, comment, status transitions, image upload, history")
public class RequestActions {

    private final RequestService requestService;

    public RequestActions(RequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "Assign a technician to the request (publishes maintenance.assigned)")
    @PostMapping(value = "/requests/{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> assign(@PathVariable String id,
                                                             @Valid @RequestBody AssignTechnicianRequest body) {
        return ResponseEntity.ok(requestService.assignTechnician(id, body));
    }

    @Operation(summary = "Add a comment (publishes maintenance.comment.added)")
    @PostMapping(value = "/requests/{id}/comment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> comment(@PathVariable String id,
                                                              @Valid @RequestBody AddCommentRequest body) {
        return ResponseEntity.ok(requestService.addComment(id, body));
    }

    @Operation(summary = "Change request status (validated against the state machine)")
    @PostMapping(value = "/requests/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> changeStatus(@PathVariable String id,
                                                                   @Valid @RequestBody StatusChangeRequest body) {
        return ResponseEntity.ok(requestService.changeStatus(id, body));
    }

    @Operation(summary = "Upload an image attachment for the request")
    @PostMapping(value = "/requests/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> uploadImage(@PathVariable String id,
                                                                  @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(requestService.uploadImage(id, file));
    }

    @Operation(summary = "Get the status-change history for a request")
    @GetMapping("/requests/{id}/history")
    public ResponseEntity<List<MaintenanceRequestResponse.HistoryEntryResponse>> history(@PathVariable String id) {
        return ResponseEntity.ok(requestService.getHistory(id));
    }
}
