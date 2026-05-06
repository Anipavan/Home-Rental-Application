package com.spa.home_rental_application.analytics_service.analytics_service.repository;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.OccupancyStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OccupancyStatRepository extends JpaRepository<OccupancyStat, String> {
    Optional<OccupancyStat> findByBuildingIdAndStatDate(String buildingId, LocalDate date);
    List<OccupancyStat> findByBuildingIdOrderByStatDateAsc(String buildingId);
    List<OccupancyStat> findByStatDate(LocalDate date);
}
