package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.SendNotificationRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.NotificationResponse;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationType;
import com.spa.home_rental_application.notification_service.notification_service.service.NotificationService;
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
@RequestMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Notifications", description = "Manual sends, history lookups, status checks")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Send an email notification (manual / admin)")
    @PostMapping(value = "/send/email", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationResponse> sendEmail(@Valid @RequestBody SendNotificationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.EMAIL)));
    }

    @Operation(summary = "Send an SMS notification (manual / admin)")
    @PostMapping(value = "/send/sms", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationResponse> sendSms(@Valid @RequestBody SendNotificationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.SMS)));
    }

    @Operation(summary = "Send a push notification (manual / admin)")
    @PostMapping(value = "/send/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationResponse> sendPush(@Valid @RequestBody SendNotificationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(forced(body, NotificationType.PUSH)));
    }

    @Operation(summary = "All notifications (paginated)")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.list(pageable));
    }

    @Operation(summary = "All notifications for a user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationResponse>> byUser(@PathVariable String userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @Operation(summary = "Notification by id (status check)")
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> byId(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @Operation(summary = "Same as /{id} — kept for backwards compatibility with the design doc")
    @GetMapping("/{id}/status")
    public ResponseEntity<NotificationResponse> status(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /** The /send/email|sms|push endpoints all share one DTO; force the type from the path. */
    private SendNotificationRequest forced(SendNotificationRequest req, NotificationType t) {
        return new SendNotificationRequest(
                req.userId(), t, req.category(),
                req.subject(), req.message(), req.recipient(),
                req.templateVariables());
    }
}
