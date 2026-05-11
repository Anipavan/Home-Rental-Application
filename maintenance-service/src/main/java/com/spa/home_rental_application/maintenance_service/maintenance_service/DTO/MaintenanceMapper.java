package com.spa.home_rental_application.maintenance_service.maintenance_service.DTO;

import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;

import java.util.List;
import java.util.stream.Collectors;

/** Entity ↔ response-DTO mapper. Controllers/services never project the entity directly. */
public final class MaintenanceMapper {

    private MaintenanceMapper() {}

    public static MaintenanceRequestResponse toResponse(MaintenanceRequest e) {
        if (e == null) return null;
        return new MaintenanceRequestResponse(
                e.getId(),
                e.getRequestNumber(),
                e.getTenantId(),
                e.getFlatId(),
                e.getOwnerId(),
                // Legacy rows (created before the discriminator field
                // existed) deserialize with kind == null; treat those
                // as MAINTENANCE so the response shape stays well-typed.
                e.getKind() == null ? Kind.MAINTENANCE : e.getKind(),
                e.getCategory(),
                e.getComplaintCategory(),
                e.getTitle(),
                e.getDescription(),
                e.getPriority(),
                e.getStatus(),
                e.getImages(),
                e.getAssignedTo(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getResolvedAt(),
                e.getClosedAt(),
                e.getComments() == null ? List.of() :
                        e.getComments().stream()
                                .map(c -> new MaintenanceRequestResponse.CommentResponse(
                                        c.getUserId(), c.getComment(), c.getTimestamp()))
                                .collect(Collectors.toList()),
                e.getHistory() == null ? List.of() :
                        e.getHistory().stream()
                                .map(h -> new MaintenanceRequestResponse.HistoryEntryResponse(
                                        h.getFromStatus(), h.getToStatus(), h.getChangedBy(), h.getTimestamp()))
                                .collect(Collectors.toList())
        );
    }
}
