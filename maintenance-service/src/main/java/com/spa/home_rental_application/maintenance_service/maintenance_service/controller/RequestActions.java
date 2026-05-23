package com.spa.home_rental_application.maintenance_service.maintenance_service.controller;

import com.spa.home_rental_application.auth_commons.GatewayAuthFilter;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AddCommentRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.AssignTechnicianRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.StatusChangeRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /* Audit H12 + H13: assign / comment / status-change are gated to
     * the ticket's participants (tenant on the row, assigned
     * technician) or an admin. Anyone else gets 403.
     *
     * Tenants can do: comment + status (cancel only — OPEN → CLOSED).
     * Assigned vendor can do: comment + any state-machine-valid status.
     * Admin can do: anything (including reassignment).
     */

    @Operation(summary = "Assign a technician to the request (ADMIN only)")
    @PostMapping(value = "/requests/{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> assign(@PathVariable String id,
                                                             @Valid @RequestBody AssignTechnicianRequest body,
                                                             HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(requestService.assignTechnician(id, body));
    }

    @Operation(summary = "Add a comment (ticket participant or ADMIN)")
    @PostMapping(value = "/requests/{id}/comment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> comment(@PathVariable String id,
                                                              @Valid @RequestBody AddCommentRequest body,
                                                              HttpServletRequest req) {
        MaintenanceRequestResponse current = requestService.getRequestById(id);
        requireParticipantOrAdmin(current.tenantId(), current.assignedTo(), current.ownerId(), req);
        return ResponseEntity.ok(requestService.addComment(id, body));
    }

    @Operation(summary = "Change request status (state machine + participant authz)")
    @PostMapping(value = "/requests/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> changeStatus(@PathVariable String id,
                                                                   @Valid @RequestBody StatusChangeRequest body,
                                                                   HttpServletRequest req) {
        MaintenanceRequestResponse current = requestService.getRequestById(id);
        requireParticipantOrAdmin(current.tenantId(), current.assignedTo(), current.ownerId(), req);
        // Tenants can only CANCEL their own ticket (close from OPEN).
        // Any other status flip needs the assigned vendor, the owner of
        // the property, or an admin — otherwise a tenant could jump a
        // ticket straight to RESOLVED and lose audit visibility.
        if (isCallerTheTenant(current.tenantId(), req) && !isAdmin()
                && !(body.newStatus() == Status.CLOSED && current.status() == Status.OPEN)) {
            throw new AccessDeniedException(
                    "Tenants can only cancel an OPEN ticket — ask the property manager to update other states.");
        }
        return ResponseEntity.ok(requestService.changeStatus(id, body));
    }

    @Operation(summary = "Upload an image attachment for the request (participant or ADMIN)")
    @PostMapping(value = "/requests/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceRequestResponse> uploadImage(@PathVariable String id,
                                                                  @RequestParam("file") MultipartFile file,
                                                                  HttpServletRequest req) throws IOException {
        MaintenanceRequestResponse current = requestService.getRequestById(id);
        requireParticipantOrAdmin(current.tenantId(), current.assignedTo(), current.ownerId(), req);
        return ResponseEntity.ok(requestService.uploadImage(id, file));
    }

    @Operation(summary = "Get the status-change history for a request (participant or ADMIN)")
    @GetMapping("/requests/{id}/history")
    public ResponseEntity<List<MaintenanceRequestResponse.HistoryEntryResponse>> history(@PathVariable String id,
                                                                                          HttpServletRequest req) {
        MaintenanceRequestResponse current = requestService.getRequestById(id);
        requireParticipantOrAdmin(current.tenantId(), current.assignedTo(), current.ownerId(), req);
        return ResponseEntity.ok(requestService.getHistory(id));
    }

    /* ────────── authz helpers ────────── */

    private static boolean isCallerTheTenant(String tenantId, HttpServletRequest req) {
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        return tenantId != null && tenantId.equals(caller);
    }

    /**
     * A ticket "participant" is the tenant who raised it, the technician
     * assigned to fix it, OR the owner of the flat that the ticket is
     * about. Earlier this method only knew about tenant + assignedTo,
     * which meant the owner clicking "Start" / "Mark resolved" on their
     * own building's ticket got rejected by Spring's
     * AccessDeniedException — surfaced as a generic 500 (per the global
     * handler) because nothing maps AccessDeniedException to 403 here.
     *
     * <p>Adding {@code ownerId} closes that gap: the maintenance entity
     * already stores ownerId at create-time (resolved from the flat),
     * so we just pass it through and accept the owner as a participant.
     */
    private static void requireParticipantOrAdmin(String tenantId,
                                                  String assignedTo,
                                                  String ownerId,
                                                  HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) return;  // system path
        if (tenantId != null && tenantId.equals(caller)) return;
        if (assignedTo != null && assignedTo.equals(caller)) return;
        if (ownerId != null && ownerId.equals(caller)) return;
        throw new AccessDeniedException(
                "You can only act on maintenance requests you're a participant on.");
    }

    private static void requireAdmin(HttpServletRequest req) {
        if (isAdmin()) return;
        String caller = req.getHeader(GatewayAuthFilter.HDR_UID);
        if (caller == null || caller.isBlank()) return;  // system path
        throw new AccessDeniedException("Admin role required.");
    }

    private static boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }
}
