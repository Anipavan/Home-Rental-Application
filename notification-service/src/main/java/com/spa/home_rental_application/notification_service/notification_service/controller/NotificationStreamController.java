package com.spa.home_rental_application.notification_service.notification_service.controller;

import com.spa.home_rental_application.notification_service.notification_service.service.NotificationStreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint backing the SPA notification bell's
 * real-time push.
 *
 * <p>The SPA opens one stream per signed-in user when the
 * NotificationBell mounts. The connection stays open indefinitely;
 * heartbeats every 25s keep proxies / load-balancers from killing
 * the idle TCP. When a new INAPP notification is persisted (via
 * {@link com.spa.home_rental_application.notification_service.notification_service.service.NotificationService}'s
 * fan-out path) it lands on this stream within ~30 ms and the SPA
 * invalidates its react-query cache for that user.
 *
 * <p>Auth: the SPA uses {@code event-source-polyfill} so it can send
 * the standard {@code Authorization: Bearer …} header on the EventSource
 * request — the gateway then forwards {@code X-Auth-User-Id} like
 * every other request. We cross-check the path param against that
 * header so a user can't subscribe to someone else's stream by
 * guessing their authUserId.
 */
@RestController
@RequestMapping("/notifications/stream")
@Slf4j
@Tag(name = "Notifications · Stream",
        description = "Server-Sent Events push of bell notifications")
public class NotificationStreamController {

    private final NotificationStreamRegistry registry;

    public NotificationStreamController(NotificationStreamRegistry registry) {
        this.registry = registry;
    }

    @Operation(summary = "Subscribe to the bell push stream for the given user")
    @GetMapping(value = "/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable String userId,
                                              HttpServletRequest req) {
        // Identity check — the gateway pre-populates X-Auth-User-Id.
        // Trust the header over the path so a guessed userId in the
        // URL can't snoop someone else's bell.
        String authedId = req.getHeader("X-Auth-User-Id");
        if (authedId != null && !authedId.isBlank() && !authedId.equals(userId)) {
            log.warn("SSE subscribe denied: header userId={} != path userId={}",
                    authedId, userId);
            return ResponseEntity.status(403).build();
        }
        SseEmitter emitter = registry.register(userId);
        return ResponseEntity.ok(emitter);
    }
}
