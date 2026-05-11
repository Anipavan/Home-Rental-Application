package com.spa.home_rental_application.notification_service.notification_service.service;

import com.spa.home_rental_application.notification_service.notification_service.entities.NotificationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory registry of active Server-Sent-Events streams, indexed by
 * {@code authUserId}. The SPA bell opens one stream per signed-in user
 * via {@code GET /notifications/stream}; this registry holds the
 * {@link SseEmitter} so {@link NotificationService} can push a payload
 * the moment a notification is persisted.
 *
 * <p>Why in-memory, not Redis:
 * <ul>
 *   <li>Single notification-service instance in the current deploy.
 *       Scaling out requires either Redis pub/sub fanning to all
 *       instances, or sticky sessions. Not yet needed.</li>
 *   <li>SSE connections are inherently bound to a JVM — the
 *       connection terminates when the process restarts anyway.</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #register} on connect — also wires the emitter's
 *       onCompletion / onTimeout / onError so terminated streams
 *       drop out of the map on their own.</li>
 *   <li>{@link #pushToUser} on each new notification — fans out to
 *       every emitter under that user; cleans up failures inline.</li>
 *   <li>{@link #heartbeat} every 25s sends an SSE comment line so
 *       proxies / load balancers don't drop the idle connection.
 *       Standard for SSE deployments behind nginx / Spring Cloud
 *       Gateway.</li>
 * </ul>
 */
@Component
@Slf4j
public class NotificationStreamRegistry {

    /** Open streams per user. CopyOnWriteArrayList → safe concurrent iterate + push. */
    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /** Stream-id sequence — handy for logs so we can trace a single connection. */
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * Returns a fresh {@link SseEmitter} subscribed to {@code userId}'s
     * push channel. The caller (controller) hands this to Spring MVC
     * which keeps the response open.
     *
     * <p>Timeout: 0L (infinite) — we keep the stream alive forever and
     * rely on heartbeats + client reconnection on disconnect. SSE
     * clients reconnect automatically on EOF, so even if we ever close
     * a stream the SPA picks back up within seconds.
     */
    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        int id = seq.incrementAndGet();
        log.info("SSE-{} registered for userId={}", id, userId);

        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter, "completed", id));
        emitter.onTimeout(() -> remove(userId, emitter, "timeout", id));
        emitter.onError(ex -> remove(userId, emitter, "error: " + ex.getMessage(), id));

        // First message is a handshake the SPA can match on to confirm
        // the connection actually established (vs sitting in connect
        // state for 30s with no traffic).
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("userId", userId, "streamId", id)));
        } catch (IOException ex) {
            // Initial handshake failed — kill the emitter so the
            // client retries. Don't keep a dead one in the map.
            log.warn("SSE-{} handshake failed for userId={}: {}", id, userId, ex.getMessage());
            remove(userId, emitter, "handshake-failed", id);
        }
        return emitter;
    }

    /**
     * Push the freshly-persisted {@link NotificationLog} to every
     * connected stream for its user. Best-effort — emitters that fail
     * mid-push (broken pipe, client disconnect we haven't yet caught)
     * are pruned inline.
     */
    public void pushToUser(NotificationLog log) {
        if (log == null || log.getUserId() == null) return;
        List<SseEmitter> emitters = emittersByUser.get(log.getUserId());
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(buildPayload(log)));
            } catch (Exception ex) {
                // Don't let a single bad emitter stop the fan-out.
                NotificationStreamRegistry.log.debug(
                        "SSE push failed for userId={}: {} — pruning emitter",
                        log.getUserId(), ex.getMessage());
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Spring's task scheduler ticks this every 25 seconds. Sends an
     * SSE comment line (":heartbeat\n\n") to every active stream so
     * intermediate proxies don't close the idle connection. Comments
     * don't fire any client-side event handlers, so the SPA stays
     * blissfully unaware.
     */
    @Scheduled(fixedDelay = 25_000L, initialDelay = 25_000L)
    public void heartbeat() {
        int total = 0;
        for (Map.Entry<String, List<SseEmitter>> entry : emittersByUser.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    total++;
                } catch (Exception ex) {
                    entry.getValue().remove(emitter);
                    try { emitter.complete(); } catch (Exception ignored) {}
                }
            }
        }
        if (total > 0) {
            log.debug("SSE heartbeat sent to {} active stream(s)", total);
        }
    }

    /** Convenience for tests / observability. */
    public int activeStreamCount() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }

    /* -------------------- internal -------------------- */

    private static Map<String, Object> buildPayload(NotificationLog n) {
        // Match the on-wire shape of NotificationResponse so the SPA
        // can drop the payload directly into its react-query cache.
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", n.getId());
        m.put("userId", n.getUserId());
        m.put("type", n.getType() == null ? null : n.getType().name());
        m.put("category", n.getCategory() == null ? null : n.getCategory().name());
        m.put("subject", n.getSubject());
        m.put("message", n.getMessage());
        m.put("status", n.getStatus() == null ? null : n.getStatus().name());
        m.put("sentAt", n.getSentAt() == null ? null : n.getSentAt().toString());
        return m;
    }

    private void remove(String userId, SseEmitter emitter, String reason, int id) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) emittersByUser.remove(userId);
        }
        log.debug("SSE-{} for userId={} ended: {}", id, userId, reason);
    }
}
