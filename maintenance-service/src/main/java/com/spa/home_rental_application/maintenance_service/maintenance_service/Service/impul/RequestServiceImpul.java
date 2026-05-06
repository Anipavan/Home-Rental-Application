package com.spa.home_rental_application.maintenance_service.maintenance_service.Service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.MaintenanceServiceEvents.*;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.MaintenanceServiceEvents;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.MaintenanceMapper;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.*;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.CategoryStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.ResolutionTimeStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Repository.MaintenanceRequestRepository;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import com.spa.home_rental_application.maintenance_service.maintenance_service.exception.IllegalStatusTransitionException;
import com.spa.home_rental_application.maintenance_service.maintenance_service.exception.RecordNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class RequestServiceImpul implements RequestService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<Status> PENDING_STATUSES =
            EnumSet.of(Status.OPEN, Status.IN_PROGRESS);
    private static final Set<Status> ACTIVE_STATUSES =
            EnumSet.of(Status.OPEN, Status.IN_PROGRESS, Status.RESOLVED);

    private final MaintenanceRequestRepository repo;
    private final MaintenanceServiceEvents events;
    private final String uploadDir;

    public RequestServiceImpul(MaintenanceRequestRepository repo,
                               MaintenanceServiceEvents events,
                               @Value("${app.uploads.dir:uploads/maintenance}") String uploadDir) {
        this.repo = repo;
        this.events = events;
        this.uploadDir = uploadDir;
    }

    /* ---------- Lifecycle ---------- */

    @Override
    public MaintenanceRequestResponse createRequest(CreateRequestDto dto) {
        log.info("createRequest tenant={} flat={} category={} priority={}",
                dto.tenantId(), dto.flatId(), dto.category(), dto.priority());

        Instant now = Instant.now();
        MaintenanceRequest entity = MaintenanceRequest.builder()
                .requestNumber(generateRequestNumber())
                .tenantId(dto.tenantId())
                .flatId(dto.flatId())
                .ownerId(dto.ownerId())
                .category(dto.category())
                .title(dto.title())
                .description(dto.description())
                .priority(dto.priority())
                .status(Status.OPEN)
                .images(new ArrayList<>())
                .comments(new ArrayList<>())
                .history(new ArrayList<>(List.of(MaintenanceRequest.HistoryEntry.builder()
                        .fromStatus(null)
                        .toStatus(Status.OPEN)
                        .changedBy(dto.tenantId())
                        .timestamp(now)
                        .build())))
                .createdAt(now)
                .updatedAt(now)
                .build();

        MaintenanceRequest saved = saveWithNumberRetry(entity);

        events.sendMaintenanceCreated(MaintenanceCreatedEvent.builder()
                .eventType("maintenance.created")
                .requestId(saved.getId())
                .requestNumber(saved.getRequestNumber())
                .tenantId(saved.getTenantId())
                .flatId(saved.getFlatId())
                .category(saved.getCategory().name())
                .priority(saved.getPriority().name())
                .timestamp(Instant.now())
                .build());

        return MaintenanceMapper.toResponse(saved);
    }

    @Override
    public MaintenanceRequestResponse updateRequest(String id, UpdateRequestDto dto) {
        MaintenanceRequest existing = mustFind(id);
        if (dto.category()    != null) existing.setCategory(dto.category());
        if (dto.title()       != null) existing.setTitle(dto.title());
        if (dto.description() != null) existing.setDescription(dto.description());
        if (dto.priority()    != null) existing.setPriority(dto.priority());
        existing.setUpdatedAt(Instant.now());
        return MaintenanceMapper.toResponse(repo.save(existing));
    }

    @Override
    public void deleteRequest(String id) {
        if (!repo.existsById(id)) {
            throw new RecordNotFoundException("Maintenance request not found: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    public MaintenanceRequestResponse getRequestById(String id) {
        return MaintenanceMapper.toResponse(mustFind(id));
    }

    @Override
    public Page<MaintenanceRequestResponse> getAllRequests(Pageable pageable) {
        return repo.findAll(pageable).map(MaintenanceMapper::toResponse);
    }

    /* ---------- Lookups ---------- */

    @Override
    public List<MaintenanceRequestResponse> getRequestsByStatus(Status status) {
        return repo.findByStatus(status).stream().map(MaintenanceMapper::toResponse).toList();
    }

    @Override
    public List<MaintenanceRequestResponse> getRequestsByPriority(Priority priority) {
        return repo.findByPriority(priority).stream().map(MaintenanceMapper::toResponse).toList();
    }

    @Override
    public List<MaintenanceRequestResponse> getRequestsByCategory(Category category) {
        return repo.findByCategory(category).stream().map(MaintenanceMapper::toResponse).toList();
    }

    @Override
    public List<MaintenanceRequestResponse> getRequestsByTenant(String tenantId) {
        return repo.findByTenantId(tenantId).stream().map(MaintenanceMapper::toResponse).toList();
    }

    @Override
    public List<MaintenanceRequestResponse> getRequestsByOwner(String ownerId) {
        return repo.findByOwnerId(ownerId).stream().map(MaintenanceMapper::toResponse).toList();
    }

    /* ---------- Actions ---------- */

    @Override
    public MaintenanceRequestResponse assignTechnician(String id, AssignTechnicianRequest body) {
        MaintenanceRequest existing = mustFind(id);
        existing.setAssignedTo(body.assignedTo());
        // First assignment auto-transitions OPEN → IN_PROGRESS for clarity
        if (existing.getStatus() == Status.OPEN) {
            recordTransition(existing, Status.IN_PROGRESS, body.assignedTo());
            existing.setStatus(Status.IN_PROGRESS);
        }
        existing.setUpdatedAt(Instant.now());
        MaintenanceRequest saved = repo.save(existing);

        events.sendMaintenanceAssigned(MaintenanceAssignedEvent.builder()
                .eventType("maintenance.assigned")
                .requestId(saved.getId())
                .tenantId(saved.getTenantId())
                .assignedTo(saved.getAssignedTo())
                .timestamp(Instant.now())
                .build());
        return MaintenanceMapper.toResponse(saved);
    }

    @Override
    public MaintenanceRequestResponse addComment(String id, AddCommentRequest body) {
        MaintenanceRequest existing = mustFind(id);
        Instant now = Instant.now();
        if (existing.getComments() == null) existing.setComments(new ArrayList<>());
        existing.getComments().add(MaintenanceRequest.Comment.builder()
                .userId(body.userId()).comment(body.comment()).timestamp(now).build());
        existing.setUpdatedAt(now);
        MaintenanceRequest saved = repo.save(existing);

        events.sendMaintenanceCommentAdded(MaintenanceCommentAddedEvent.builder()
                .eventType("maintenance.comment.added")
                .requestId(saved.getId())
                .userId(body.userId())
                .comment(body.comment())
                .timestamp(now)
                .build());
        return MaintenanceMapper.toResponse(saved);
    }

    @Override
    public MaintenanceRequestResponse changeStatus(String id, StatusChangeRequest body) {
        MaintenanceRequest existing = mustFind(id);
        Status from = existing.getStatus();
        Status to = body.newStatus();
        if (from == to) {
            return MaintenanceMapper.toResponse(existing);
        }
        if (!from.canTransitionTo(to)) {
            throw new IllegalStatusTransitionException(
                    "Illegal status transition: " + from + " → " + to);
        }
        Instant now = Instant.now();
        recordTransition(existing, to, body.changedBy());
        existing.setStatus(to);
        if (to == Status.RESOLVED) existing.setResolvedAt(now);
        if (to == Status.CLOSED)   existing.setClosedAt(now);
        existing.setUpdatedAt(now);
        MaintenanceRequest saved = repo.save(existing);

        events.sendMaintenanceStatusChanged(MaintenanceStatusChangedEvent.builder()
                .eventType("maintenance.status.changed")
                .requestId(saved.getId())
                .tenantId(saved.getTenantId())
                .oldStatus(from.name())
                .newStatus(to.name())
                .changedBy(body.changedBy())
                .timestamp(now)
                .build());

        if (to == Status.RESOLVED) {
            long minutes = Duration.between(saved.getCreatedAt(), saved.getResolvedAt()).toMinutes();
            events.sendMaintenanceResolved(MaintenanceResolvedEvent.builder()
                    .eventType("maintenance.resolved")
                    .requestId(saved.getId())
                    .tenantId(saved.getTenantId())
                    .resolutionTimeMinutes(minutes)
                    .timestamp(now)
                    .build());
        }
        return MaintenanceMapper.toResponse(saved);
    }

    @Override
    public MaintenanceRequestResponse uploadImage(String id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File must not be empty");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("File exceeds 5 MB");
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Unsupported content type: " + ct);
        }
        MaintenanceRequest existing = mustFind(id);

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String safeOriginal = file.getOriginalFilename() == null
                ? "img" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = dir.resolve(UUID.randomUUID() + "_" + safeOriginal);
        Files.write(target, file.getBytes());

        if (existing.getImages() == null) existing.setImages(new ArrayList<>());
        existing.getImages().add(target.toString());
        existing.setUpdatedAt(Instant.now());
        return MaintenanceMapper.toResponse(repo.save(existing));
    }

    @Override
    public List<MaintenanceRequestResponse.HistoryEntryResponse> getHistory(String id) {
        MaintenanceRequest existing = mustFind(id);
        return existing.getHistory() == null ? List.of() :
                existing.getHistory().stream()
                        .map(h -> new MaintenanceRequestResponse.HistoryEntryResponse(
                                h.getFromStatus(), h.getToStatus(), h.getChangedBy(), h.getTimestamp()))
                        .toList();
    }

    /* ---------- Analytics ---------- */

    @Override
    public long getPendingRequestCount() {
        return repo.countByStatusIn(PENDING_STATUSES);
    }

    @Override
    public List<CategoryStatsResponse> getCategoryStats() {
        List<CategoryStatsResponse> out = new ArrayList<>(Category.values().length);
        for (Category c : Category.values()) {
            out.add(new CategoryStatsResponse(c, repo.countByCategory(c)));
        }
        return out;
    }

    @Override
    public ResolutionTimeStatsResponse getResolutionTimeStats() {
        List<MaintenanceRequest> resolved = repo.findByStatus(Status.RESOLVED);
        if (resolved.isEmpty()) {
            return new ResolutionTimeStatsResponse(0, 0d, 0L, 0L);
        }
        long min = Long.MAX_VALUE, max = 0L, total = 0L;
        int considered = 0;
        for (MaintenanceRequest r : resolved) {
            if (r.getCreatedAt() == null || r.getResolvedAt() == null) continue;
            long minutes = Duration.between(r.getCreatedAt(), r.getResolvedAt()).toMinutes();
            min = Math.min(min, minutes);
            max = Math.max(max, minutes);
            total += minutes;
            considered++;
        }
        if (considered == 0) {
            return new ResolutionTimeStatsResponse(0, 0d, 0L, 0L);
        }
        double avg = (double) total / considered;
        return new ResolutionTimeStatsResponse(considered, avg, min, max);
    }

    /* ---------- Kafka consumer side-effect ---------- */

    /**
     * Auto-close every active request (OPEN/IN_PROGRESS/RESOLVED) for the
     * vacated flat. This runs from the {@code FlatVacatedListener} which is
     * the Kafka subscriber.
     */
    @Override
    public void onFlatVacated(String flatId, String tenantId) {
        List<MaintenanceRequest> affected = repo.findByFlatIdAndStatusIn(flatId, ACTIVE_STATUSES);
        if (affected.isEmpty()) return;
        Instant now = Instant.now();
        String actor = "SYSTEM (flat.vacated)";
        for (MaintenanceRequest r : affected) {
            Status from = r.getStatus();
            recordTransition(r, Status.CLOSED, actor);
            r.setStatus(Status.CLOSED);
            r.setClosedAt(now);
            r.setUpdatedAt(now);
            repo.save(r);

            events.sendMaintenanceStatusChanged(MaintenanceStatusChangedEvent.builder()
                    .eventType("maintenance.status.changed")
                    .requestId(r.getId())
                    .tenantId(r.getTenantId())
                    .oldStatus(from.name())
                    .newStatus(Status.CLOSED.name())
                    .changedBy(actor)
                    .timestamp(now)
                    .build());
        }
        log.info("Auto-closed {} request(s) for vacated flat {}", affected.size(), flatId);
    }

    /* ---------- Helpers ---------- */

    private MaintenanceRequest mustFind(String id) {
        return repo.findById(id).orElseThrow(
                () -> new RecordNotFoundException("Maintenance request not found: " + id));
    }

    private void recordTransition(MaintenanceRequest entity, Status to, String actor) {
        if (entity.getHistory() == null) entity.setHistory(new ArrayList<>());
        entity.getHistory().add(MaintenanceRequest.HistoryEntry.builder()
                .fromStatus(entity.getStatus())
                .toStatus(to)
                .changedBy(actor)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Human-friendly request number: {@code MR-YYMMDD-XXXXXXXX}.
     *
     * <p>The suffix uses the first 8 hex chars of a {@link UUID} —
     * ~4.3 billion possibilities per day — making same-day collisions
     * vanishingly rare. The save path additionally retries with a fresh
     * number on the off-chance a collision still happens (see
     * {@link #saveWithNumberRetry}).
     */
    private String generateRequestNumber() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "MR-" + date + "-" + suffix;
    }

    /**
     * Save the entity, regenerating its requestNumber and retrying on the
     * extremely unlikely event of a unique-index collision. Caps at 5
     * attempts so a misconfigured DB can't cause an infinite loop.
     */
    private MaintenanceRequest saveWithNumberRetry(MaintenanceRequest entity) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return repo.save(entity);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                String fresh = generateRequestNumber();
                log.warn("requestNumber collision on '{}' (attempt {}); retrying with '{}'",
                        entity.getRequestNumber(), attempt + 1, fresh);
                entity.setRequestNumber(fresh);
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique requestNumber after 5 attempts — DB may be misconfigured.");
    }
}
