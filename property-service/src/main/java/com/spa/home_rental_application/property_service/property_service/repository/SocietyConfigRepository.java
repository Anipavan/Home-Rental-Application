package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.SocietyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Society config lookups. The two non-trivial queries are by
 * building_id (the most common path — owner / maintainer dashboard
 * loads the config for the building they're viewing) and by
 * public_view_token (the shareable read-only URL). Both columns
 * are unique-indexed so these are O(1) on the index.
 */
@Repository
public interface SocietyConfigRepository extends JpaRepository<SocietyConfig, String> {

    Optional<SocietyConfig> findByBuildingId(String buildingId);

    Optional<SocietyConfig> findByPublicViewToken(String publicViewToken);

    /** All societies managed by a given maintainer — powers the
     *  future MAINTAINER dashboard ("show me every building I
     *  maintain"). In MVP only one row per owner-self-maintained
     *  building. */
    List<SocietyConfig> findByMaintainerUserId(String maintainerUserId);
}
