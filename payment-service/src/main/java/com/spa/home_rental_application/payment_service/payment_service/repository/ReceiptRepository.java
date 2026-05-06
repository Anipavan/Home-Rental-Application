package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, String> {
    Optional<Receipt> findByPaymentId(String paymentId);
}
