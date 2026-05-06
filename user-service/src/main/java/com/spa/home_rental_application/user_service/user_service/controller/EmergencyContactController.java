package com.spa.home_rental_application.user_service.user_service.controller;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.service.EmergencyContactService;
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
@RequestMapping(value = "/users/contacts", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Emergency Contacts", description = "User emergency-contact management")
public class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    public EmergencyContactController(EmergencyContactService emergencyContactService) {
        this.emergencyContactService = emergencyContactService;
    }

    @Operation(summary = "Create an emergency contact")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergencyContactResponseDto> create(@RequestBody @Valid EmergencyContactRequestDto body) {
        log.info("POST /users/contacts userId={}", body.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(emergencyContactService.saveUsersEmergencyContact(body));
    }

    @Operation(summary = "List all emergency contacts (paginated)")
    @GetMapping
    public ResponseEntity<Page<EmergencyContactResponseDto>> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int pagenum,
            @RequestParam(defaultValue = "10") @Min(1) int pageSize) {
        Pageable pageable = PageRequest.of(pagenum, pageSize);
        return ResponseEntity.ok(emergencyContactService.getAllContacts(pageable));
    }

    @Operation(summary = "List emergency contacts for a specific user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<EmergencyContactResponseDto>> getByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(emergencyContactService.getAllContactsByUserId(userId));
    }

    @Operation(summary = "Update an emergency contact")
    @PutMapping(value = "/{contactId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmergencyContactResponseDto> update(
            @PathVariable String contactId,
            @RequestBody @Valid EmergencyContactRequestDto body) {
        return ResponseEntity.ok(emergencyContactService.UpdateEmergencyContact(body, contactId));
    }

    @Operation(summary = "Delete an emergency contact")
    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> delete(@PathVariable String contactId) {
        emergencyContactService.DeleteEmergencyContact(contactId);
        return ResponseEntity.noContent().build();
    }
}
