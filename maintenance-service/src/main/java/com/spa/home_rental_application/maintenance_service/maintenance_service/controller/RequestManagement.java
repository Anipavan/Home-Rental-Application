package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.CreateRequestDto;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.UpdateRequestDto;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
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

import java.util.List;

@RestController
@RequestMapping(value = "/maintenance", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Maintenance Requests", description = "Lifecycle CRUD + lookup endpoints (handles both maintenance tickets and complaints)")
public class RequestManagement {

    private final RequestService requestService;

    public RequestManagement(RequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "Create a maintenance request OR a complaint (kind discriminator)",
            description = "Default kind = MAINTENANCE. Set kind=COMPLAINT plus complaintCategory to file a grievance.")
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> create(@Valid @RequestBody CreateRequestDto body) {
        log.info("POST /maintenance/requests kind={} tenant={} flat={}",
                body.kind(), body.tenantId(), body.flatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.createRequest(body));
    }

    @Operation(summary = "List all requests (paginated, optionally filtered by kind)")
    @GetMapping("/requests")
    public ResponseEntity<Page<MaintenanceRequestResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @RequestParam(required = false) Kind kind) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(kind == null
                ? requestService.getAllRequests(pageable)
                : requestService.getAllByKind(kind, pageable));
    }

    @Operation(summary = "Get a maintenance request by id")
    @GetMapping("/requests/{id}")
    public ResponseEntity<MaintenanceRequestResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(requestService.getRequestById(id));
    }

    @Operation(summary = "Update mutable attributes (category/title/description/priority)")
    @PutMapping(value = "/requests/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> update(@PathVariable String id,
                                                             @Valid @RequestBody UpdateRequestDto body) {
        return ResponseEntity.ok(requestService.updateRequest(id, body));
    }

    @Operation(summary = "Delete a maintenance request")
    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        requestService.deleteRequest(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Filter by status")
    @GetMapping("/requests/status/{status}")
    public ResponseEntity<List<MaintenanceRequestResponse>> byStatus(@PathVariable Status status) {
        return ResponseEntity.ok(requestService.getRequestsByStatus(status));
    }

    @Operation(summary = "Filter by priority")
    @GetMapping("/requests/priority/{priority}")
    public ResponseEntity<List<MaintenanceRequestResponse>> byPriority(@PathVariable Priority priority) {
        return ResponseEntity.ok(requestService.getRequestsByPriority(priority));
    }

    @Operation(summary = "Filter by category")
    @GetMapping("/requests/category/{category}")
    public ResponseEntity<List<MaintenanceRequestResponse>> byCategory(@PathVariable Category category) {
        return ResponseEntity.ok(requestService.getRequestsByCategory(category));
    }

    @Operation(summary = "List all requests for a tenant (optionally filtered by kind)")
    @GetMapping("/requests/tenant/{tenantId}")
    public ResponseEntity<List<MaintenanceRequestResponse>> byTenant(
            @PathVariable String tenantId,
            @RequestParam(required = false) Kind kind) {
        return ResponseEntity.ok(kind == null
                ? requestService.getRequestsByTenant(tenantId)
                : requestService.getByTenantAndKind(tenantId, kind));
    }

    @Operation(summary = "List all requests for an owner (optionally filtered by kind)")
    @GetMapping("/requests/owner/{ownerId}")
    public ResponseEntity<List<MaintenanceRequestResponse>> byOwner(
            @PathVariable String ownerId,
            @RequestParam(required = false) Kind kind) {
        return ResponseEntity.ok(kind == null
                ? requestService.getRequestsByOwner(ownerId)
                : requestService.getByOwnerAndKind(ownerId, kind));
    }

    @Operation(summary = "Count of pending (OPEN/IN_PROGRESS) requests, optionally filtered by kind")
    @GetMapping("/requests/pending-count")
    public ResponseEntity<Long> pendingCount(@RequestParam(required = false) Kind kind) {
        return ResponseEntity.ok(kind == null
                ? requestService.getPendingRequestCount()
                : requestService.getPendingCountByKind(kind));
    }
}
