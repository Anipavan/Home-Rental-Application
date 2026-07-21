package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.SocietyAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SocietyAnnouncementRepository
        extends JpaRepository<SocietyAnnouncement, String> {

    /** Newest-first list for one building. Backed by the (building_id,
     *  created_at DESC) composite index. */
    List<SocietyAnnouncement> findByBuildingIdOrderByCreatedAtDesc(String buildingId);
}
