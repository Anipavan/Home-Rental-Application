package com.spa.home_rental_application.api_gateway.api_gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces the default Spring whitelabel error page with a JSON envelope
 * that matches the shape used by every downstream service.
 */
@Configuration
public class GatewayErrorHandler {

    @Bean
    @Order(-2)  // before the default error handler
    public CustomErrorWebExceptionHandler errorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        CustomErrorWebExceptionHandler h = new CustomErrorWebExceptionHandler(
                errorAttributes, webProperties.getResources(), applicationContext);
        h.setMessageWriters(serverCodecConfigurer.getWriters());
        h.setMessageReaders(serverCodecConfigurer.getReaders());
        return h;
    }

    @Slf4j
    public static class CustomErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

        public CustomErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                              WebProperties.Resources resources,
                                              ApplicationContext applicationContext) {
            super(errorAttributes, resources, applicationContext);
        }

        @Override
        protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
            return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
        }

        private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
            Map<String, Object> attrs = getErrorAttributes(request, ErrorAttributeOptions.defaults());
            int status = (int) attrs.getOrDefault("status", 500);
            HttpStatus httpStatus = HttpStatus.resolve(status);
            if (httpStatus == null) httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", LocalDateTime.now().toString());
            body.put("status", httpStatus.value());
            body.put("error", httpStatus.getReasonPhrase());
            body.put("message", attrs.getOrDefault("message", httpStatus.getReasonPhrase()));
            body.put("errorCode", "GATEWAY_ERROR");
            body.put("path", request.path());

            log.warn("Gateway error: {} on {}", httpStatus, request.path());
            return ServerResponse.status(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(body));
        }
    }
}
