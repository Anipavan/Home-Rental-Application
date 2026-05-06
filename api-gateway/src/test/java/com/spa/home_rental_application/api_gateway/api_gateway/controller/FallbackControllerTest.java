package com.spa.home_rental_application.api_gateway.api_gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the fallback envelope. We don't spin a WebFluxContext
 * here because the controller is plain-blocking and the assertions only
 * care about the body / status / headers it builds.
 */
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void perServiceFallback_returnsServiceNameInBody() {
        ResponseEntity<Map<String, Object>> resp = controller.fallbackForService("auth");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("service")).isEqualTo("auth");
        assertThat(resp.getBody().get("errorCode")).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(resp.getBody().get("status")).isEqualTo(503);
        assertThat((String) resp.getBody().get("message")).contains("auth");
        assertThat(resp.getHeaders().getFirst("Retry-After")).isEqualTo("10");
    }

    @Test
    void genericFallback_marksServiceUnknown() {
        ResponseEntity<Map<String, Object>> resp = controller.fallbackGeneric();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("service")).isEqualTo("unknown");
        assertThat(resp.getBody().get("retryAfterSeconds")).isEqualTo(10);
    }

    @Test
    void everyKnownServiceName_isAccepted() {
        for (String svc : new String[]{
                "auth", "property", "user", "payment",
                "maintenance", "notification", "analytics"}) {
            ResponseEntity<Map<String, Object>> resp = controller.fallbackForService(svc);
            assertThat(resp.getBody().get("service"))
                    .as("service name in body for %s", svc)
                    .isEqualTo(svc);
        }
    }
}
