package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.CreateVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.RespondToVisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.VisitRequestResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.VisitRequest;
import com.spa.home_rental_application.notification_service.notification_service.exception.NotificationNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.VisitRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class VisitRequestService {

    private final VisitRequestRepository repository;
    private final EnquiryAutoResponder autoResponder;

    public VisitRequestService(VisitRequestRepository repository,
                               EnquiryAutoResponder autoResponder) {
        this.repository = repository;
        this.autoResponder = autoResponder;
    }

    public VisitRequestResponse create(CreateVisitRequest req) {
        log.info("New visit request from userId={} flatId={} preferredAt={}",
                req.userId(), req.flatId(), req.preferredAt());
        VisitRequest v = VisitRequest.builder()
                .userId(req.userId())
                .visitorName(req.visitorName())
                .visitorEmail(req.visitorEmail())
                .visitorPhone(req.visitorPhone())
                .flatId(req.flatId())
                .buildingId(req.buildingId())
                .propertyLabel(req.propertyLabel())
                .preferredAt(req.preferredAt())
                .message(req.message())
                .contextUrl(req.contextUrl())
                .status("PENDING")
                .build();
        VisitRequest saved = repository.save(v);
        autoResponder.onVisitRequestCreated(saved);
        return toResponse(saved);
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
        return toResponse(repository.save(v));
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
