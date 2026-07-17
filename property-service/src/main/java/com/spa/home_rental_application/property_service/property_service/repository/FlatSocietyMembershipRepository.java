package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.FlatSocietyMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Composite-key repository for {@link FlatSocietyMembership}. Read
 * paths favour the {@code (flat_id, user_id)} PK for point lookups
 * and the two secondary indexes for the maintainer + maintainee
 * dashboards.
 */
public interface FlatSocietyMembershipRepository
        extends JpaRepository<FlatSocietyMembership, FlatSocietyMembership.PK> {

    Optional<FlatSocietyMembership> findByFlatIdAndUserId(String flatId, String userId);

    /** Active society members of one flat — feeds the maintainer's
     *  per-flat resident list and the maintenance-billing job. */
    List<FlatSocietyMembership> findByFlatIdAndIsActiveTrue(String flatId);

    /** Every flat this user is currently a society member of — used
     *  by the maintainee's Society page to enumerate their charges. */
    List<FlatSocietyMembership> findByUserIdAndIsActiveTrue(String userId);

    /** Bulk fetch for the maintainer's dashboard: pull membership rows
     *  for every flat in the building in one query, then group in
     *  memory. */
    List<FlatSocietyMembership> findByFlatIdInAndIsActiveTrue(Collection<String> flatIds);
}
