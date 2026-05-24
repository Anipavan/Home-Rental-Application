package com.spa.home_rental_application.payment_service.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.PaymentServiceEvents.PaymentOverdueEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.PaymentServiceEvents;
import com.spa.home_rental_application.payment_service.payment_service.entities.OutboxEvent;
import com.spa.home_rental_application.payment_service.payment_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Audit H24: transactional outbox publisher.
 *
 * <p>Two responsibilities split into one class:
 *
 * <ol>
 *   <li><b>{@link #stageOverdue}</b> — called from the late-fee
 *       scheduler INSIDE the same DB transaction as the OVERDUE
 *       flip. It writes a row to {@code payment_outbox_events}
 *       carrying the serialized {@code PaymentOverdueEvent}. Because
 *       it shares the transaction, either both the state change
 *       AND the event-row land, or neither does — no "OVERDUE
 *       persisted but tenant never notified" gap.</li>
 *   <li><b>{@link #publishPending}</b> — runs on a 30-second
 *       schedule. Picks up PENDING outbox rows, pushes them to
 *       Kafka via the existing {@link PaymentServiceEvents}
 *       producer, marks them PUBLISHED. Broker hiccups are caught,
 *       attempt count incremented, and the row is left PENDING for
 *       the next cycle. After 10 attempts the row goes FAILED so
 *       it stops blocking newer events; ops investigates.</li>
 * </ol>
 *
 * <p>Why not just Kafka transactions? The Spring Kafka transactional
 * producer would only solve the publish-side atomicity — it doesn't
 * help when the DB commit succeeds but Kafka is down (the case the
 * audit specifically flagged). Outbox is the standard fix.
 */
@Service
@Slf4j
public class OutboxPublisher {

    private static final String STATUS_PENDING   = "PENDING";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_FAILED    = "FAILED";

    private final OutboxEventRepository repo;
    private final PaymentServiceEvents kafkaEvents;
    private final ObjectMapper mapper;

    /** Stop retrying after this many attempts — keeps poison-pill rows from blocking the queue. */
    @Value("${app.payment.outbox.max-attempts:10}")
    private int maxAttempts;

    /** Max rows pulled per publish cycle — keeps the catchup loop bounded. */
    @Value("${app.payment.outbox.batch-size:50}")
    private int batchSize;

    /**
     * Self-reference (injected through the Spring proxy) so we can call
     * {@link #publishOne(OutboxEvent)} via the proxy and actually have
     * its {@code REQUIRES_NEW} transactional boundary respected.
     *
     * <p>Without this, the in-class call {@code publishOne(e)} from
     * {@link #publishPending()} bypasses the Spring proxy entirely —
     * Spring's transaction interceptor never gets a chance to start a
     * new transaction. The whole batch then ran in one tx, and a
     * single poison-pill event would roll back the status updates of
     * every event that "succeeded" in-memory earlier in the loop.
     * Routing through {@code self.publishOne(e)} fixes that.
     */
    private final OutboxPublisher self;

    public OutboxPublisher(OutboxEventRepository repo,
                           PaymentServiceEvents kafkaEvents,
                           ObjectMapper mapper,
                           @Lazy @Autowired OutboxPublisher self) {
        this.repo = repo;
        this.kafkaEvents = kafkaEvents;
        this.mapper = mapper;
        this.self = self;
    }

    /**
     * Stage a payment.overdue event. MUST be called inside the same
     * @Transactional scope as the OVERDUE flip — the caller's
     * transaction propagates here so the outbox row commits in
     * lockstep with the payment row.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void stageOverdue(PaymentOverdueEvent event) {
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // Should never happen for a Lombok-generated POJO — but
            // if it does, log loudly and skip the staging. Better to
            // miss the alert than crash the whole batch.
            log.error("Outbox: refusing to stage event with un-serializable payload (paymentId={})",
                    event.getPaymentId(), ex);
            return;
        }
        repo.save(OutboxEvent.builder()
                .eventType(event.getEventType())
                .aggregateId(event.getPaymentId())
                .payload(payload)
                .status(STATUS_PENDING)
                .build());
    }

    /**
     * Drain a batch of pending outbox rows to Kafka. Runs every 30s
     * by default. Each row is published in its own transaction so a
     * broker failure on row N doesn't roll back rows 1..N-1.
     */
    @Scheduled(fixedDelayString = "${app.payment.outbox.publish-interval-ms:30000}",
               initialDelayString = "${app.payment.outbox.initial-delay-ms:15000}")
    public void publishPending() {
        List<OutboxEvent> batch = repo.findPendingBatch(maxAttempts, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;
        log.debug("Outbox: publishing {} pending event(s)", batch.size());
        for (OutboxEvent e : batch) {
            // Route through the proxy (self.publishOne) so each event
            // gets its own REQUIRES_NEW transaction. Direct `publishOne(e)`
            // would skip the Spring proxy → no new tx → poison event
            // rolls back the whole batch (see field-level Javadoc on
            // `self` for the full reasoning).
            self.publishOne(e);
        }
    }

    /**
     * Per-event publish — own transaction so a failure rolls back
     * the status update for THIS row only, not the whole batch.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void publishOne(OutboxEvent e) {
        try {
            if ("payment.overdue".equals(e.getEventType())) {
                PaymentOverdueEvent payload = mapper.readValue(e.getPayload(), PaymentOverdueEvent.class);
                kafkaEvents.sendPaymentOverdue(payload);
            } else {
                // Unknown type — log + mark FAILED so it doesn't keep
                // retrying. New event types must add a case here.
                log.warn("Outbox: unknown eventType={} on row={} — marking FAILED", e.getEventType(), e.getId());
                e.setStatus(STATUS_FAILED);
                e.setLastError("Unknown eventType");
                repo.save(e);
                return;
            }
            e.setStatus(STATUS_PUBLISHED);
            e.setPublishedAt(Instant.now());
            e.setLastError(null);
            repo.save(e);
        } catch (Exception ex) {
            int next = (e.getAttempts() == null ? 0 : e.getAttempts()) + 1;
            e.setAttempts(next);
            e.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            if (next >= maxAttempts) {
                e.setStatus(STATUS_FAILED);
                log.error("Outbox row {} hit max attempts ({}) — marking FAILED. Last error: {}",
                        e.getId(), maxAttempts, ex.getMessage());
            } else {
                log.warn("Outbox publish failed for row={} (attempt {}/{}): {} — will retry",
                        e.getId(), next, maxAttempts, ex.getMessage());
            }
            repo.save(e);
        }
    }
}
