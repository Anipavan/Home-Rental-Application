package com.spa.home_rental_application.analytics_service.analytics_service.repository;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.MaintenanceMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaintenanceMetricRepository extends JpaRepository<MaintenanceMetric, String> {
    Optional<MaintenanceMetric> findByCategory(String category);
}
