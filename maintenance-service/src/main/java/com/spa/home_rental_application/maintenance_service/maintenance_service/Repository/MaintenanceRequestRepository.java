package com.spa.home_rental_application.maintenance_service.maintenance_service.Repository;

import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MaintenanceRequestRepository extends MongoRepository<MaintenanceRequest, String> {

    Page<MaintenanceRequest> findAll(Pageable pageable);

    List<MaintenanceRequest> findByStatus(Status status);
    long countByStatusIn(Collection<Status> statuses);

    List<MaintenanceRequest> findByPriority(Priority priority);

    List<MaintenanceRequest> findByCategory(Category category);
    long countByCategory(Category category);

    List<MaintenanceRequest> findByTenantId(String tenantId);
    List<MaintenanceRequest> findByOwnerId(String ownerId);

    List<MaintenanceRequest> findByFlatIdAndStatusIn(String flatId, Collection<Status> statuses);

    /* ─────────────────────── Kind-scoped queries ────────────────────────────
     *
     * Background: the `kind` field was added to existing documents
     * mid-lifecycle. Rows written before the migration have no `kind`
     * key at all, NOT a null value — so the auto-derived
     * {@code findByKind(MAINTENANCE)} translates to
     * {@code {kind: "MAINTENANCE"}}, which does not match
     * field-absent documents.
     *
     * Result without the explicit @Query: every legacy ticket vanishes
     * from /app/maintenance the moment this code ships. Catastrophic.
     *
     * Fix: when querying for MAINTENANCE, also accept docs where the
     * field is missing. COMPLAINT stays strict — complaints only exist
     * post-migration, so there's no legacy class to absorb.
     */

    @Query("{ 'kind': { $in: [?0, null] } }")
    Page<MaintenanceRequest> findByKindIncludingLegacy(Kind kind, Pageable pageable);

    @Query("{ 'tenantId': ?0, 'kind': { $in: [?1, null] } }")
    List<MaintenanceRequest> findByTenantIdAndKindIncludingLegacy(String tenantId, Kind kind);

    @Query("{ 'ownerId': ?0, 'kind': { $in: [?1, null] } }")
    List<MaintenanceRequest> findByOwnerIdAndKindIncludingLegacy(String ownerId, Kind kind);

    @Query(value = "{ 'kind': { $in: [?0, null] } }", count = true)
    long countByKindIncludingLegacy(Kind kind);

    @Query(value = "{ 'kind': { $in: [?0, null] }, 'status': { $in: ?1 } }", count = true)
    long countByKindAndStatusInIncludingLegacy(Kind kind, Collection<Status> statuses);

    /* Strict variants — used for COMPLAINT-side queries where there are
     * no legacy rows to worry about. */
    Page<MaintenanceRequest> findByKind(Kind kind, Pageable pageable);
    List<MaintenanceRequest> findByTenantIdAndKind(String tenantId, Kind kind);
    List<MaintenanceRequest> findByOwnerIdAndKind(String ownerId, Kind kind);
    long countByKind(Kind kind);
    long countByKindAndStatusIn(Kind kind, Collection<Status> statuses);
}
