package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.PaymentReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, String> {
    List<PaymentReminder> findByPaymentId(String paymentId);
}
