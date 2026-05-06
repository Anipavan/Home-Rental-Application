package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Page<Payment> findAll(Pageable pageable);

    List<Payment> findByTenantId(String tenantId);
    List<Payment> findByOwnerId(String ownerId);
    List<Payment> findByFlatIdAndStatusIn(String flatId, Collection<PaymentStatus> statuses);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.dueDate < :cutoff")
    List<Payment> findOverdueCandidates(PaymentStatus status, LocalDate cutoff);

    @Query("SELECT p FROM Payment p WHERE p.status = com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus.PENDING AND p.dueDate = :date")
    List<Payment> findPendingDueOn(LocalDate date);
}
