package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.PreferenceRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.PreferenceResponse;
import com.spa.home_rental_application.notification_service.notification_service.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/notifications/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notification Preferences", description = "Per-user channel preferences and category opt-outs")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @Operation(summary = "Get a user's notification preferences (defaults if none set)")
    @GetMapping("/{userId}")
    public ResponseEntity<PreferenceResponse> get(@PathVariable String userId) {
        return ResponseEntity.ok(service.get(userId));
    }

    @Operation(summary = "Upsert a user's notification preferences")
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PreferenceResponse> upsert(@PathVariable String userId,
                                                     @Valid @RequestBody PreferenceRequest body) {
        return ResponseEntity.ok(service.upsert(userId, body));
    }
}
