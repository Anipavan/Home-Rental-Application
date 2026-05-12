package com.spa.home_rental_application.property_service.property_service.controller;

import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.FlatFavorite;
import com.spa.home_rental_application.property_service.property_service.repository.FlatFavoriteRepo;
import com.spa.home_rental_application.property_service.property_service.service.FlatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wishlist / saved-listings endpoints.
 *
 * <p>Mounted at {@code /properties/favorites/**} so the gateway routes
 * {@code /rentals/v1/properties/favorites/**} here transparently — same
 * prefix the rest of the property service uses, no new route entry
 * required on the gateway side.
 *
 * <p>Auth model: the user id comes from the gateway-supplied
 * {@code X-Auth-User-Id} header (populated by {@code JWTAuthenticationFilter}
 * upstream). We deliberately don't accept it from a path / body
 * parameter — that would let a logged-in user wishlist things for
 * someone else.
 */
@RestController
@RequestMapping(value = "/properties/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Slf4j
@Tag(name = "Favourites", description = "Tenant wishlist — saved-for-later flats")
public class FlatFavoriteController {

    private static final String AUTH_USER_ID_HEADER = "X-Auth-User-Id";

    private final FlatFavoriteRepo repo;
    private final FlatService flatService;

    public FlatFavoriteController(FlatFavoriteRepo repo, FlatService flatService) {
        this.repo = repo;
        this.flatService = flatService;
    }

    /**
     * Mark a flat as saved. Idempotent — calling on an already-saved
     * flat returns 200 with the existing row instead of 409, so the
     * heart-toggle UX doesn't need a "have I already favourited this"
     * preflight.
     */
    @Operation(summary = "Save a flat to the current user's wishlist (idempotent)")
    @PostMapping("/{flatId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> add(
            @PathVariable @NotBlank String flatId,
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        log.info("Wishlist add userId={} flatId={}", authUserId, flatId);
        // Validate the flat exists — guards against poisoning the
        // wishlist with dead ids. Throws RecordNotFound which the
        // global handler maps to 404.
        flatService.getflatById(flatId);

        // Audit M9: TOCTOU between findByUserIdAndFlatId and repo.save
        // — two concurrent toggles would both miss the existing-row
        // check and both attempt insert. The DB unique constraint on
        // (user_id, flat_id) guarantees only one survives; catch the
        // DataIntegrityViolation and re-fetch instead of bubbling
        // 500 to the client. End result: heart-toggle is truly
        // idempotent even under concurrent fire.
        FlatFavorite saved;
        try {
            saved = repo.findByUserIdAndFlatId(authUserId, flatId).orElseGet(() ->
                    repo.save(FlatFavorite.builder()
                            .userId(authUserId)
                            .flatId(flatId)
                            .build()));
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // Concurrent insert lost the race — re-read the winning row.
            saved = repo.findByUserIdAndFlatId(authUserId, flatId).orElseThrow(
                    () -> new IllegalStateException(
                            "Concurrent favourite insert lost the race AND the winning row vanished — should be impossible.",
                            dup));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", saved.getId());
        body.put("userId", saved.getUserId());
        body.put("flatId", saved.getFlatId());
        body.put("createdAt", saved.getCreatedAt());
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    /**
     * Remove a flat from the wishlist. Idempotent — returns 204 even
     * if the row didn't exist, so unsaving twice in a row doesn't
     * surface as an error.
     */
    @Operation(summary = "Remove a flat from the current user's wishlist (idempotent)")
    @DeleteMapping("/{flatId}")
    @Transactional
    public ResponseEntity<Void> remove(
            @PathVariable @NotBlank String flatId,
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        long n = repo.deleteByUserIdAndFlatId(authUserId, flatId);
        log.info("Wishlist remove userId={} flatId={} deletedRows={}", authUserId, flatId, n);
        return ResponseEntity.noContent().build();
    }

    /**
     * The wishlist page itself — newest-saved first. Returns the
     * full FlatResponseDTO so the FE can render the same card UX as
     * the browse page (cover image, rent, BHK, etc.) without a
     * second fetch.
     */
    @Operation(summary = "List the current user's saved flats (newest first)")
    @GetMapping
    public ResponseEntity<List<FlatResponseDTO>> list(
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        List<FlatFavorite> rows = repo.findByUserIdOrderByCreatedAtDesc(authUserId);
        if (rows.isEmpty()) return ResponseEntity.ok(List.of());

        // Hydrate to FlatResponseDTOs. Skip rows whose flat has been
        // soft-deleted (getflatById throws) so the wishlist self-heals
        // when a listing is taken down — no 404 in the middle of a
        // list response.
        // Preserve the "newest favourited first" order from the repo
        // query — mapping through hydrateSafe keeps the same index
        // ordering. We don't re-sort by flat.createdAt because the
        // wishlist is keyed on when the user saved, not when the
        // listing was created.
        List<FlatResponseDTO> hydrated = rows.stream()
                .map(this::hydrateSafe)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(hydrated);
    }

    /**
     * Light projection — just the set of flatIds the user has saved.
     * Powers the heart-icon "filled vs outlined" state on
     * /browse and /property/:id without hydrating every flat. The FE
     * caches this for the session.
     */
    @Operation(summary = "Lightweight: flatIds the current user has saved (for heart-icon state)")
    @GetMapping("/ids")
    public ResponseEntity<Set<String>> ids(
            @RequestHeader(AUTH_USER_ID_HEADER) String authUserId) {
        requireAuth(authUserId);
        return ResponseEntity.ok(new java.util.HashSet<>(repo.findFlatIdsByUserId(authUserId)));
    }

    /* ───────────── helpers ───────────── */

    /**
     * Defensive guard against a blank {@code X-Auth-User-Id} header.
     * The gateway sets this from the JWT's {@code uid} claim — when
     * the claim is missing (legacy tokens, mis-minted JWTs), the
     * header arrives as an empty string. Oracle treats {@code ""} as
     * {@code NULL}, so any per-user INSERT (favourite, etc.) explodes
     * with ORA-01400 unless we reject the request here first.
     */
    private static void requireAuth(String authUserId) {
        if (authUserId == null || authUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user id is missing — please sign in again");
        }
    }

    private FlatResponseDTO hydrateSafe(FlatFavorite f) {
        try {
            return flatService.getflatById(f.getFlatId());
        } catch (Exception ex) {
            log.debug("Wishlist row userId={} flatId={} hydration failed (likely deleted): {}",
                    f.getUserId(), f.getFlatId(), ex.getMessage());
            return null;
        }
    }
}
