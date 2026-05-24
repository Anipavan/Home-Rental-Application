package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.Payment;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Page<Payment> findAll(Pageable pageable);

    List<Payment> findByTenantId(String tenantId);
    List<Payment> findByOwnerId(String ownerId);
    List<Payment> findByFlatIdAndStatusIn(String flatId, Collection<PaymentStatus> statuses);

    /**
     * Lookup every payment whose flat is in the given collection.
     * Used by {@code getPaymentsByOwner} to back-fill the legacy rows
     * that were auto-created (via {@code onFlatOccupied}) before this
     * fix and therefore have {@code ownerId=null}. The owner's full
     * flat list is resolved via the {@link com.spa.home_rental_application.payment_service.payment_service.client.PropertyClient}
     * Feign call and fed in here.
     */
    List<Payment> findByFlatIdIn(Collection<String> flatIds);

    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Audit L4: paginated, deterministically-ordered status lookup
     * for the collections-ops overdue dashboard. Oldest-overdue first
     * mirrors the natural triage order.
     */
    Page<Payment> findByStatusOrderByDueDateAsc(PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.dueDate < :cutoff")
    List<Payment> findOverdueCandidates(PaymentStatus status, LocalDate cutoff);

    @Query("SELECT p FROM Payment p WHERE p.status = com.spa.home_rental_application.payment_service.payment_service.enums.PaymentStatus.PENDING AND p.dueDate = :date")
    List<Payment> findPendingDueOn(LocalDate date);

    /**
     * Audit H25: lookup by the gateway-assigned order id. Razorpay
     * webhooks carry the order_id (the gateway's id, not ours) in
     * the payment entity, so we use it to find the matching local
     * payment row instead of relying on the broken {@code order_id ==
     * paymentId} assumption the previous code had.
     */
    java.util.Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /**
     * Pessimistic SELECT … FOR UPDATE on the payment row. Used by
     * {@code markPaid} to serialize the status flip across two
     * concurrent paths: the synchronous /verify call from the
     * frontend after the user returns from the gateway, AND the
     * asynchronous webhook from the gateway itself. Without this
     * both paths could pass the "status != PAID" check between
     * their SELECT and UPDATE, double-credit the payment, and emit
     * two payment.completed Kafka events / two receipts / two
     * notification emails.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    java.util.Optional<Payment> findByIdForUpdate(String id);
}
