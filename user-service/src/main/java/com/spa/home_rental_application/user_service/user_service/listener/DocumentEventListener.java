package com.spa.home_rental_application.user_service.user_service.listener;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Subscribes to {@code document-events.document.extracted}. When OCR /
 * Document AI returns structured data for an Aadhaar or PAN card, we
 * auto-fill any blank fields on the user's profile.
 * <p>
 * We never overwrite already-populated fields — the user's manual edits win.
 */
@Component
@Slf4j
public class DocumentEventListener {

    private final UserRepo userRepo;

    public DocumentEventListener(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @KafkaListener(
            topics = "${app.kafka.document-topic:document-events}",
            groupId = "${spring.kafka.consumer.group-id:hra-user-service}-document-extracted",
            properties = {"spring.json.value.default.type=com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents.DocumentExtractedEvent"}
    )
    @Transactional
    public void onExtracted(DocumentExtractedEvent e) {
        if (e == null || !"document.extracted".equals(e.getEventType())) return;
        if (e.getUserId() == null) {
            log.warn("document.extracted with null userId — skipping");
            return;
        }
        String docType = e.getDocumentType();
        if (docType == null) return;

        // Only auto-fill from AADHAAR / PAN — other document types don't map
        // cleanly onto profile fields.
        if (!"AADHAAR".equalsIgnoreCase(docType) && !"PAN".equalsIgnoreCase(docType)) {
            log.debug("Ignoring document.extracted for type={} (not AADHAAR/PAN)", docType);
            return;
        }

        User user = userRepo.findActiveById(e.getUserId()).orElse(null);
        if (user == null) {
            log.warn("document.extracted for unknown userId={}", e.getUserId());
            return;
        }

        Map<String, String> fields = e.getExtractedData();
        if (fields == null || fields.isEmpty()) {
            log.debug("document.extracted carried no fields for userId={}", e.getUserId());
            return;
        }

        boolean changed = false;
        // Auto-fill firstName when blank
        if (isBlank(user.getFirstName()) && fields.get("name") != null) {
            String[] parts = fields.get("name").trim().split("\\s+", 2);
            user.setFirstName(parts[0]);
            if (parts.length > 1 && isBlank(user.getLastName())) {
                user.setLastName(parts[1]);
            }
            changed = true;
        }
        // Auto-fill DOB when blank
        if (user.getDateOfBirth() == null && fields.get("dob") != null) {
            try {
                user.setDateOfBirth(LocalDate.parse(fields.get("dob")));
                changed = true;
            } catch (DateTimeParseException ignored) {
                log.debug("Could not parse extracted dob='{}' — skipping", fields.get("dob"));
            }
        }
        // Auto-fill address when blank (Aadhaar carries it)
        if (isBlank(user.getAddress()) && fields.get("address") != null) {
            user.setAddress(fields.get("address"));
            changed = true;
        }

        if (changed) {
            user.setUpdatedAt(LocalDateTime.now());
            userRepo.save(user);
            log.info("Auto-filled profile from {} for userId={}", docType, e.getUserId());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
