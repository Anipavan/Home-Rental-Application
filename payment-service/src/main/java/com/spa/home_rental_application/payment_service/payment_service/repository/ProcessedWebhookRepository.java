package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.ProcessedWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, String> {

    /**
     * Idempotency primary lookup. The unique constraint on
     * {@code (gateway_name, event_key)} guarantees at most one row per
     * (gateway, event) pair; we use this Optional read as the fast-path
     * "have we already processed this event?" check before attempting
     * the insert.
     */
    Optional<ProcessedWebhook> findByGatewayNameAndEventKey(String gatewayName, String eventKey);
}
