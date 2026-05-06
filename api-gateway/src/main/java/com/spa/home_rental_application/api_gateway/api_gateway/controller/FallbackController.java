package com.spa.home_rental_application.api_gateway.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returned by Spring Cloud Gateway's per-route circuit-breaker filter when a
 * downstream service is open-circuited, slow-call-failed, or times out (>10s).
 *
 * Each gateway route declares a fallbackUri of the shape
 * {@code forward:/__cb/fallback/<service>}; the {service} path variable lets
 * us tell the caller which service tripped the breaker without leaking the
 * underlying exception.
 *
 * The single catch-all mapping handles GET, POST, PUT, PATCH, DELETE, OPTIONS
 * because Gateway forwards the original request method to the fallback URI.
 */
@RestController
@RequestMapping("/__cb")
public class FallbackController {

    /**
     * Per-service fallback. The {service} segment is whatever came after
     * {@code /__cb/fallback/} in the route's fallbackUri (e.g. "auth",
     * "property", "user", "payment", "maintenance", "notification",
     * "analytics").
     */
    @RequestMapping(
            value = "/fallback/{service}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fallbackForService(@PathVariable String service) {
        return build(service);
    }

    /**
     * Generic fallback — kept for backwards compatibility with any older
     * route definitions that point to {@code forward:/__cb/fallback}.
     */
    @RequestMapping(
            value = "/fallback",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fallbackGeneric() {
        return build("unknown");
    }

    private ResponseEntity<Map<String, Object>> build(String service) {
        // LinkedHashMap so JSON keys come out in this stable order.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        body.put("service", service);
        body.put("message",
                "The " + service + " service is temporarily unavailable. "
                        + "The circuit breaker is open or the request timed out. "
                        + "Please retry shortly.");
        body.put("errorCode", "SERVICE_UNAVAILABLE");
        body.put("retryAfterSeconds", 10);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(body);
    }
}
