package com.spa.home_rental_application.analytics_service.analytics_service.repository;

import com.spa.home_rental_application.analytics_service.analytics_service.entities.PaymentTrend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTrendRepository extends JpaRepository<PaymentTrend, String> {
    Optional<PaymentTrend> findByOwnerIdAndYearAndMonth(String ownerId, int year, int month);
    List<PaymentTrend> findByOwnerIdOrderByYearAscMonthAsc(String ownerId);
}
