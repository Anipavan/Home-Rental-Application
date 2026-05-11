package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.CreateVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.RespondToVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.VisitRequestResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.VisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.enums.NotificationCategory;
import com.spa.home_rental_application.notification_service.notification_service.exception.NotificationNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.VisitRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
public class VisitRequestService {

    private final VisitRequestRepository repository;
    private final EnquiryAutoResponder autoResponder;
    private final NotificationService notifications;

    public VisitRequestService(VisitRequestRepository repository,
                               EnquiryAutoResponder autoResponder,
                               NotificationService notifications) {
        this.repository = repository;
        this.autoResponder = autoResponder;
        this.notifications = notifications;
    }

    public VisitRequestResponse create(CreateVisitRequest req) {
        log.info("New visit request from userId={} flatId={} ownerId={} preferredAt={}",
                req.userId(), req.flatId(), req.ownerId(), req.preferredAt());
        VisitRequest v = VisitRequest.builder()
                .userId(req.userId())
                .visitorName(req.visitorName())
                .visitorEmail(req.visitorEmail())
                .visitorPhone(req.visitorPhone())
                .flatId(req.flatId())
                .buildingId(req.buildingId())
                .ownerId(blankToNull(req.ownerId()))
                .propertyLabel(req.propertyLabel())
                .preferredAt(req.preferredAt())
                .message(req.message())
                .contextUrl(req.contextUrl())
                .status("PENDING")
                .build();
        VisitRequest saved = repository.save(v);
        autoResponder.onVisitRequestCreated(saved);
        // Multi-channel ping for the owner: in-app bell + email + SMS
        // + WhatsApp (last two only fire if the owner has a phone +
        // matching opt-in). NoBroker/99acres send all three on a
        // visit request because owners who get pinged across channels
        // respond noticeably faster than owners on email alone.
        if (saved.getOwnerId() != null && !saved.getOwnerId().isBlank()) {
            notifications.fanOut(saved.getOwnerId(),
                    NotificationCategory.VISIT_REQUESTED,
                    Map.of(
                            "propertyLabel", safeStr(saved.getPropertyLabel()),
                            "visitorName",   safeStr(saved.getVisitorName()),
                            "preferredAt",   saved.getPreferredAt() == null
                                    ? "" : saved.getPreferredAt().toString(),
                            "message",       saved.getMessage() == null
                                    ? "" : saved.getMessage()
                    ));
        }
        return toResponse(saved);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public Page<VisitRequestResponse> listByStatus(String status, Pageable pageable) {
        return repository.findByStatusOrderByPreferredAtAsc(status, pageable)
                .map(this::toResponse);
    }

    public Page<VisitRequestResponse> listByFlat(String flatId, Pageable pageable) {
        return repository.findByFlatIdOrderByPreferredAtAsc(flatId, pageable)
                .map(this::toResponse);
    }

    public Page<VisitRequestResponse> listByUser(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByPreferredAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /** Visit requests against the given owner's buildings. */
    public Page<VisitRequestResponse> listByOwner(String ownerId, Pageable pageable) {
        return repository.findByOwnerIdOrderByPreferredAtAsc(ownerId, pageable)
                .map(this::toResponse);
    }

    public long pendingCountForOwner(String ownerId) {
        return repository.countByOwnerIdAndStatus(ownerId, "PENDING");
    }

    public Page<VisitRequestResponse> listBetween(Instant from, Instant to,
                                                  Pageable pageable) {
        return repository.findByPreferredAtBetweenOrderByPreferredAtAsc(from, to, pageable)
                .map(this::toResponse);
    }

    public VisitRequestResponse respond(String id, RespondToVisitRequest req) {
        VisitRequest v = repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Visit request not found: " + id));
        v.setAdminResponse(req.adminResponse());
        v.setRespondedBy(req.respondedBy());
        v.setRespondedAt(Instant.now());
        v.setStatus(req.newStatus().toUpperCase());
        VisitRequest saved = repository.save(v);
        // Multi-channel ping for the visitor — confirmed / cancelled /
        // rescheduled. Skip PUBLIC_VISITOR (anonymous schedule-a-visit
        // submission with no platform account to address).
        if (saved.getUserId() != null && !"PUBLIC_VISITOR".equals(saved.getUserId())) {
            String statusLine = saved.getStatus() == null
                    ? "updated"
                    : saved.getStatus().toLowerCase();
            notifications.fanOut(saved.getUserId(),
                    NotificationCategory.VISIT_RESPONDED,
                    Map.of(
                            "visitorName",    safeStr(saved.getVisitorName()),
                            "propertyLabel",  safeStr(saved.getPropertyLabel()),
                            "status",         statusLine,
                            "adminResponse",  saved.getAdminResponse() == null
                                    ? "" : saved.getAdminResponse()
                    ));
        }
        return toResponse(saved);
    }

    private static String safeStr(String s) {
        return s == null || s.isBlank() ? "the property" : s;
    }

    public VisitRequestResponse getById(String id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Visit request not found: " + id));
    }

    public long pendingCount() {
        return repository.countByStatus("PENDING");
    }

    private VisitRequestResponse toResponse(VisitRequest v) {
        return new VisitRequestResponse(
                v.getId(),
                v.getUserId(),
                v.getVisitorName(),
                v.getVisitorEmail(),
                v.getVisitorPhone(),
                v.getFlatId(),
                v.getBuildingId(),
                v.getOwnerId(),
                v.getPropertyLabel(),
                v.getPreferredAt(),
                v.getMessage(),
                v.getContextUrl(),
                v.getStatus(),
                v.getAdminResponse(),
                v.getRespondedBy(),
                v.getRespondedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
