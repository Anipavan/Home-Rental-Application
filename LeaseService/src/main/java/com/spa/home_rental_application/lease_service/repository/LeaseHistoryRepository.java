package com.spa.home_rental_application.lease_service.repository;

import com.spa.home_rental_application.lease_service.Entities.LeaseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaseHistoryRepository extends JpaRepository<LeaseHistory, String> {

    List<LeaseHistory> findByLeaseIdOrderByChangedAtDesc(String leaseId);
}
