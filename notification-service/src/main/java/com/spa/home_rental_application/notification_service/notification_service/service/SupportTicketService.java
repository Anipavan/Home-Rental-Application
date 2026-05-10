package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.CreateSupportTicketRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Request.RespondToTicketRequest;
import com.spa.home_rental_application.notification_service.notification_service.DTO.Response.SupportTicketResponse;
import com.spa.home_rental_application.notification_service.notification_service.entities.SupportTicket;
import com.spa.home_rental_application.notification_service.notification_service.exception.NotificationNotFoundException;
import com.spa.home_rental_application.notification_service.notification_service.repository.SupportTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class SupportTicketService {

    private final SupportTicketRepository repository;
    private final EnquiryAutoResponder autoResponder;

    public SupportTicketService(SupportTicketRepository repository,
                                EnquiryAutoResponder autoResponder) {
        this.repository = repository;
        this.autoResponder = autoResponder;
    }

    public SupportTicketResponse create(CreateSupportTicketRequest req) {
        log.info("New support ticket from userId={} ownerId={} subject={}",
                req.userId(), req.ownerId(), req.subject());
        SupportTicket t = SupportTicket.builder()
                .userId(req.userId())
                .userName(req.userName())
                .userEmail(req.userEmail())
                .userRole(req.userRole())
                .ownerId(blankToNull(req.ownerId()))
                .subject(req.subject())
                .message(req.message())
                .contextUrl(req.contextUrl())
                .status("OPEN")
                .build();
        SupportTicket saved = repository.save(t);
        autoResponder.onSupportTicketCreated(saved);
        return toResponse(saved);
    }

    public Page<SupportTicketResponse> listByStatus(String status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::toResponse);
    }

    public Page<SupportTicketResponse> listByUser(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /** Property enquiries the given owner should see in their inbox. */
    public Page<SupportTicketResponse> listByOwner(String ownerId, Pageable pageable) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable)
                .map(this::toResponse);
    }

    public long openCountForOwner(String ownerId) {
        return repository.countByOwnerIdAndStatus(ownerId, "OPEN");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public SupportTicketResponse respond(String id, RespondToTicketRequest req) {
        SupportTicket t = repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Support ticket not found: " + id));
        t.setAdminResponse(req.adminResponse());
        t.setRespondedBy(req.respondedBy());
        t.setRespondedAt(Instant.now());
        t.setStatus(req.newStatus().toUpperCase());
        return toResponse(repository.save(t));
    }

    public SupportTicketResponse getById(String id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "Support ticket not found: " + id));
    }

    public long openCount() {
        return repository.countByStatus("OPEN");
    }

    private SupportTicketResponse toResponse(SupportTicket t) {
        return new SupportTicketResponse(
                t.getId(),
                t.getUserId(),
                t.getUserName(),
                t.getUserEmail(),
                t.getUserRole(),
                t.getOwnerId(),
                t.getSubject(),
                t.getMessage(),
                t.getContextUrl(),
                t.getStatus(),
                t.getAdminResponse(),
                t.getRespondedBy(),
                t.getRespondedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
