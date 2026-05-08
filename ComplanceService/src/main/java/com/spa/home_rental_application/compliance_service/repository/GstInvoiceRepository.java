package com.spa.home_rental_application.compliance_service.repository;

import com.spa.home_rental_application.compliance_service.Entities.GstInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GstInvoiceRepository extends JpaRepository<GstInvoice, String> {

    Optional<GstInvoice> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);

    Optional<GstInvoice> findByInvoiceNumber(String invoiceNumber);

    List<GstInvoice> findByOwnerId(String ownerId);

    List<GstInvoice> findByOwnerIdAndInvoiceDateBetween(String ownerId, LocalDate from, LocalDate to);

    long countByOwnerIdAndInvoiceDateBetween(String ownerId, LocalDate from, LocalDate to);
}
