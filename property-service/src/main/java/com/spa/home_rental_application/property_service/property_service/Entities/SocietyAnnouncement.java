package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V17 — a building-scoped announcement the maintainer (or owner) has
 * posted for residents. Notice-board level content: water shutdowns,
 * AGM reminders, festival timings, security bulletins, etc.
 *
 * <p>Read by every resident of the building (rental tenants + active
 * society members). Write is owner / maintainer / admin.
 *
 * <p>MVP shape: title, body, timestamps, authorUserId. No pinning,
 * no attachments, no read-receipts. Add those when there's real
 * evidence they earn their keep.
 */
@Entity
@Table(name = "society_announcements", indexes = {
        @Index(name = "idx_sa_building_created",
                columnList = "building_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocietyAnnouncement {

    /** UUID assigned at creation time by the service layer. */
    @Id
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "building_id", length = 64, nullable = false)
    private String buildingId;

    /** authUserId of the poster — the maintainer or owner who wrote it.
     *  Used for the display byline ("Posted by Ram") + to gate delete
     *  (author can always delete their own; other privileged roles
     *  can override). */
    @Column(name = "author_user_id", length = 64, nullable = false)
    private String authorUserId;

    @Column(length = 200, nullable = false)
    private String title;

    /** Free-form body, capped at 4000 chars — enough for a full notice
     *  without pushing us into CLOB territory. Frontend enforces the
     *  same limit on the create form. */
    @Column(length = 4000, nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
