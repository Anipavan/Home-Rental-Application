package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.SavedSearchRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.SavedSearchResponse;
import com.spa.home_rental_application.property_service.property_service.Entities.SavedSearch;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.repository.SavedSearchRepo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Save-search CRUD. Tenants save a filter combination ("2BHK under
 * ₹30k in Indiranagar") and get an email / SMS / WhatsApp the moment
 * a matching flat is listed — the matching is done by
 * {@code SavedSearchMatcherScheduler}, which fans out via the
 * existing NotificationService.
 *
 * <p>Like {@code FlatFavoriteController}, this controller reads the
 * caller's auth user id from the gateway-supplied {@code X-Auth-User-Id}
 * header. Anonymous traffic can't save searches.
 */
@RestController
@RequestMapping(value = "/properties/saved-searches",
                produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Saved Searches",
     description = "Tenant alerts — get pinged when a new flat matches a saved filter")
public class SavedSearchController {

    private static final String AUTH_USER_ID_HEADER = "X-Auth-User-Id";

    private final SavedSearchRepo repo;

    public SavedSearchController(SavedSearchRepo repo) {
        this.repo = repo;
    }

    @Operation(summary = "Create a saved search for the current user")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<SavedSearchResponse> create(
            @Valid @RequestBody SavedSearchRequest body,
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        log.info("Saved-search create userId={} city={} bhk={} maxRent={}",
                authUserId, body.city(), body.bedrooms(), body.maxRent());

        SavedSearch saved = repo.save(SavedSearch.builder()
                .userId(authUserId)
                .name(deriveName(body))
                .city(blankToNull(body.city()))
                .bedrooms(body.bedrooms())
                .minRent(body.minRent())
                .maxRent(body.maxRent())
                .minAreaSqft(body.minAreaSqft())
                .furnishingStatus(blankToNull(body.furnishingStatus()))
                .petFriendly(body.petFriendly())
                .isActive(body.isActive() == null ? Boolean.TRUE : body.isActive())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @Operation(summary = "List the current user's saved searches, newest first")
    @GetMapping
    public ResponseEntity<List<SavedSearchResponse>> list(
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        return ResponseEntity.ok(
                repo.findByUserIdOrderByCreatedAtDesc(authUserId).stream()
                        .map(SavedSearchController::toResponse)
                        .toList());
    }

    @Operation(summary = "Update a saved search (toggle isActive, change predicates, rename)")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<SavedSearchResponse> update(
            @PathVariable String id,
            @Valid @RequestBody SavedSearchRequest body,
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        SavedSearch s = repo.findById(id).orElseThrow(
                () -> new RecordNotFoundException("Saved search not found: " + id));
        if (!authUserId.equals(s.getUserId())) {
            // Don't 403 + leak existence; 404 is the friendlier signal.
            throw new RecordNotFoundException("Saved search not found: " + id);
        }
        if (body.name() != null)             s.setName(body.name());
        if (body.city() != null)             s.setCity(blankToNull(body.city()));
        if (body.bedrooms() != null)         s.setBedrooms(body.bedrooms());
        if (body.minRent() != null)          s.setMinRent(body.minRent());
        if (body.maxRent() != null)          s.setMaxRent(body.maxRent());
        if (body.minAreaSqft() != null)      s.setMinAreaSqft(body.minAreaSqft());
        if (body.furnishingStatus() != null) s.setFurnishingStatus(blankToNull(body.furnishingStatus()));
        if (body.petFriendly() != null)      s.setPetFriendly(body.petFriendly());
        if (body.isActive() != null)         s.setIsActive(body.isActive());
        return ResponseEntity.ok(toResponse(repo.save(s)));
    }

    @Operation(summary = "Delete a saved search (idempotent — no 404 if already gone)")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> remove(
            @PathVariable String id,
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        long n = repo.deleteByIdAndUserId(id, authUserId);
        log.info("Saved-search delete userId={} id={} deletedRows={}", authUserId, id, n);
        return ResponseEntity.noContent().build();
    }

    /* ───────────── helpers ───────────── */

    private static void requireAuth(String authUserId) {
        if (authUserId == null || authUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user id is missing — please sign in again");
        }
    }

    /**
     * Derive a friendly default name when the user didn't supply one.
     * e.g. "2 BHK · Bengaluru · under ₹30,000".
     */
    private static String deriveName(SavedSearchRequest r) {
        if (r.name() != null && !r.name().isBlank()) return r.name();
        StringBuilder sb = new StringBuilder();
        if (r.bedrooms() != null) sb.append(r.bedrooms()).append(" BHK");
        if (r.city() != null && !r.city().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(r.city());
        }
        if (r.maxRent() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("under ₹").append(r.maxRent());
        }
        if (sb.length() == 0) sb.append("My saved search");
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static SavedSearchResponse toResponse(SavedSearch s) {
        return new SavedSearchResponse(
                s.getId(), s.getUserId(), s.getName(),
                s.getCity(), s.getBedrooms(),
                s.getMinRent(), s.getMaxRent(), s.getMinAreaSqft(),
                s.getFurnishingStatus(), s.getPetFriendly(),
                s.getIsActive(), s.getLastMatchedAt(), s.getCreatedAt());
    }
}
