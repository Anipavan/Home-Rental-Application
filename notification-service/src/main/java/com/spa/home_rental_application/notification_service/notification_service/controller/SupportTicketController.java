package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.CreateSupportTicketRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.RespondToTicketRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.SupportTicketResponse;
import com.spa.home_rental_application.notification_service.notification_service.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/notifications/support-tickets",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Support Tickets", description = "In-app support ticket inbox")
public class SupportTicketController {

    private final SupportTicketService service;

    public SupportTicketController(SupportTicketService service) {
        this.service = service;
    }

    @Operation(summary = "Submit a support ticket from the in-app form")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SupportTicketResponse> create(
            @Valid @RequestBody CreateSupportTicketRequest request) {
        log.info("POST /notifications/support-tickets userId={}", request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "Get a single ticket by id")
    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @Operation(summary = "Admin: list tickets by status (paginated)")
    @GetMapping
    public ResponseEntity<Page<SupportTicketResponse>> list(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listByStatus(status.toUpperCase(), p));
    }

    @Operation(summary = "List tickets submitted by a specific user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<SupportTicketResponse>> byUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listByUser(userId, p));
    }

    @Operation(summary = "Owner inbox: property-related enquiries the given owner should see")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<Page<SupportTicketResponse>> byOwner(
            @PathVariable String ownerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listByOwner(ownerId, p));
    }

    @Operation(summary = "Owner inbox: count of OPEN enquiries (sidebar badge)")
    @GetMapping("/owner/{ownerId}/count/open")
    public ResponseEntity<Map<String, Long>> openCountForOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(Map.of("count", service.openCountForOwner(ownerId)));
    }

    @Operation(summary = "Admin: respond to a ticket and update status")
    @PutMapping(value = "/{id}/respond", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SupportTicketResponse> respond(
            @PathVariable String id,
            @Valid @RequestBody RespondToTicketRequest request) {
        log.info("PUT /notifications/support-tickets/{}/respond by={}",
                id, request.respondedBy());
        return ResponseEntity.ok(service.respond(id, request));
    }

    @Operation(summary = "Admin: count of OPEN tickets (for inbox badge)")
    @GetMapping("/count/open")
    public ResponseEntity<Map<String, Long>> openCount() {
        return ResponseEntity.ok(Map.of("count", service.openCount()));
    }
}
