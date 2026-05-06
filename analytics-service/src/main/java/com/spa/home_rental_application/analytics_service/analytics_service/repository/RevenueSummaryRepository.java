package com.spa.home_rental_application.analytics_service.analytics_service.repository;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.RevenueSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RevenueSummaryRepository extends JpaRepository<RevenueSummary, String> {
    Optional<RevenueSummary> findByOwnerIdAndYearAndMonth(String ownerId, int year, int month);
    List<RevenueSummary> findByOwnerId(String ownerId);
    List<RevenueSummary> findByYearAndMonth(int year, int month);
    List<RevenueSummary> findByYear(int year);
}
