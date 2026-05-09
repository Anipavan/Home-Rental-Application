package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.CreateVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.RespondToVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.VisitRequestResponse;
import com.spa.home_rental_application.notification_service.notification_service.service.VisitRequestService;
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

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping(value = "/notifications/visit-requests",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Visit Requests",
        description = "Property-visit booking inbox — submitted from the public " +
                "property-detail page, surfaced in the admin /visit-requests queue.")
public class VisitRequestController {

    private final VisitRequestService service;

    public VisitRequestController(VisitRequestService service) {
        this.service = service;
    }

    @Operation(summary = "Submit a visit request from the property-detail page")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisitRequestResponse> create(
            @Valid @RequestBody CreateVisitRequest request) {
        log.info("POST /notifications/visit-requests userId={} flatId={}",
                request.userId(), request.flatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "Get a single visit request by id")
    @GetMapping("/{id}")
    public ResponseEntity<VisitRequestResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /**
     * Admin queue. Filter by status (default PENDING) or by date-range
     * via the {@code from} / {@code to} ISO-8601 query params. Falls back
     * to a status-only listing when range is omitted.
     */
    @Operation(summary = "Admin: list visit requests (paginated, filterable)")
    @GetMapping
    public ResponseEntity<Page<VisitRequestResponse>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        if (from != null && to != null) {
            return ResponseEntity.ok(service.listBetween(from, to, p));
        }
        return ResponseEntity.ok(service.listByStatus(status.toUpperCase(), p));
    }

    @Operation(summary = "List visit requests submitted for a given flat")
    @GetMapping("/flat/{flatId}")
    public ResponseEntity<Page<VisitRequestResponse>> byFlat(
            @PathVariable String flatId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listByFlat(flatId, p));
    }

    @Operation(summary = "List visit requests submitted by a specific user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<VisitRequestResponse>> byUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listByUser(userId, p));
    }

    @Operation(summary = "Admin: confirm/cancel/complete a visit request")
    @PutMapping(value = "/{id}/respond", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisitRequestResponse> respond(
            @PathVariable String id,
            @Valid @RequestBody RespondToVisitRequest request) {
        log.info("PUT /notifications/visit-requests/{}/respond newStatus={} by={}",
                id, request.newStatus(), request.respondedBy());
        return ResponseEntity.ok(service.respond(id, request));
    }

    @Operation(summary = "Admin: count of PENDING requests (for inbox badge)")
    @GetMapping("/count/pending")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return ResponseEntity.ok(Map.of("count", service.pendingCount()));
    }
}
