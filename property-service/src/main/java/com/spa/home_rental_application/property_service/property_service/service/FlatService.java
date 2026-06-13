package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.AssignFlatRequest;
import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatPreviewResponseDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FlatService {
    Page<FlatResponseDTO> getAllFlats(Pageable pageable);
    FlatResponseDTO getflatById(String flatId);
    FlatResponseDTO createFlat(FlatRequestDTO flatRequestDTO);
    FlatResponseDTO deleteFlatById(String flatId);
    List<FlatResponseDTO> getflatsByBuildingId(String buildId);
    List<FlatResponseDTO> getflatsByTenantId(String tenantId);
    List<FlatResponseDTO> getAllVacentFlats();
    FlatResponseDTO makeFlatVacate(String flatId);
    FlatResponseDTO updateFlat(String flatId, FlatRequestDTO flatRequestDTO);
    FlatResponseDTO assignFlat(String flatId, AssignFlatRequest req);

    /**
     * Tenant-initiated scheduled vacate (Issue #5 + Issue #4). Tenant
     * picks {@code effectiveDate} which must be at least 60 days from
     * today; backend rejects with InvalidLeasePeriodException otherwise.
     * Rejects with OutstandingDuesException if any PENDING or OVERDUE
     * rent invoices remain. Caller must be the current tenant of the
     * flat (or an admin). Returns the updated flat (still occupied,
     * with scheduledVacateDate set to the picked date).
     */
    FlatResponseDTO scheduleVacate(String flatId, java.time.LocalDate effectiveDate, String comments);

    /**
     * Cancel a previously-scheduled vacate. Only the tenant who
     * scheduled it (or admin) can cancel. Clears
     * scheduledVacateDate + vacateWarningSentAt; if the 10-day
     * warning already fired, the owner gets no follow-up notification
     * — they'll just see the cancellation reflected in their dashboard.
     */
    FlatResponseDTO cancelScheduledVacate(String flatId);

    /**
     * Internal — called by VacateScheduler when scheduledVacateDate
     * has arrived. Performs the actual vacate (markFlatVacant) and
     * fires the flat.vacated Kafka event. Idempotent: re-calling on
     * an already-vacated flat is a no-op.
     */
    FlatResponseDTO executeScheduledVacate(String flatId);

    /**
     * Geosearch: every non-deleted, non-occupied flat whose parent
     * building has a geo-pin within {@code radiusKm} kilometres of
     * the given coordinates. Pin-less buildings are excluded.
     */
    List<FlatResponseDTO> findFlatsNear(double lat, double lng, double radiusKm);

    /**
     * Public, anonymous-safe preview of a flat by
     * (buildingId, flatNumber). Used by the maintainer-signup form
     * to verify, before {@code /auth/register} fires, that the
     * applicant actually lives in the building they're applying to
     * manage. The form blocks submission unless the flat exists AND
     * is currently occupied — keeps fraudulent maintainer claims
     * out of the owner's pending queue, and avoids creating orphan
     * accounts whose only claim is going to be rejected.
     *
     * <p>Returns only {@code {exists, occupied}} — no flatId,
     * tenantId, ownerId, rent, or any other field that could be used
     * to enumerate residents. The endpoint is reachable without a
     * JWT (already covered by the gateway's public
     * {@code GET /properties/flats/**} pattern), so the response
     * shape IS the privacy boundary.
     */
    FlatPreviewResponseDTO previewFlat(String buildingId, String flatNumber);
}
