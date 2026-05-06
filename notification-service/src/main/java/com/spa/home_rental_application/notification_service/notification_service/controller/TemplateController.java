package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.DTO.NotificationMapper;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.TemplateRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.TemplateResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationTemplate;
import com.spa.home_rental_application.notification_service.notification_service.exception.TemplateNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.NotificationTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/notifications/templates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notification Templates", description = "Admin CRUD over the rendered template registry")
public class TemplateController {

    private final NotificationTemplateRepository repo;

    public TemplateController(NotificationTemplateRepository repo) {
        this.repo = repo;
    }

    @Operation(summary = "List all templates")
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list() {
        return ResponseEntity.ok(repo.findAll().stream().map(NotificationMapper::toResponse).toList());
    }

    @Operation(summary = "Create a new template")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest body) {
        NotificationTemplate t = NotificationTemplate.builder()
                .name(body.name()).category(body.category()).type(body.type())
                .subject(body.subject()).bodyTemplate(body.bodyTemplate())
                .variables(body.variables())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationMapper.toResponse(repo.save(t)));
    }

    @Operation(summary = "Update an existing template by id")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TemplateResponse> update(@PathVariable String id,
                                                   @Valid @RequestBody TemplateRequest body) {
        NotificationTemplate existing = repo.findById(id).orElseThrow(
                () -> new TemplateNotFoundException("Template not found: " + id));
        existing.setName(body.name());
        existing.setCategory(body.category());
        existing.setType(body.type());
        existing.setSubject(body.subject());
        existing.setBodyTemplate(body.bodyTemplate());
        existing.setVariables(body.variables());
        return ResponseEntity.ok(NotificationMapper.toResponse(repo.save(existing)));
    }

    @Operation(summary = "Delete a template by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new TemplateNotFoundException("Template not found: " + id);
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
