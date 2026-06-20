package com.spa.home_rental_application.auth_service.Exception;

import com.spa.home_rental_application.auth_service.Exception.DTO.APIErrorResponse;
import feign.FeignException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Global exception translator for Auth Service. Produces a consistent
 * {@link APIErrorResponse} envelope and never leaks stack traces. Covers
 * domain, validation, request-shape, persistence, security, JWT, Feign and
 * I/O failures.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /* ---------- Domain ---------- */

    @ExceptionHandler(AuthRecordNotFoundException.class)
    public ResponseEntity<APIErrorResponse> handleNotFound(AuthRecordNotFoundException ex, HttpServletRequest req) {
        log.warn("Record not found at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<APIErrorResponse> handleDuplicateUser(DuplicateUserException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<APIErrorResponse> handleInvalidToken(InvalidTokenException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getErrorCode(), req);
    }

    /* ---------- Spring Security ---------- */

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<APIErrorResponse> handleBadCreds(BadCredentialsException ex, HttpServletRequest req) {
        log.warn("Bad credentials at {}", req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", "BAD_CREDENTIALS", req);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<APIErrorResponse> handleUserNotFound(UsernameNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", "BAD_CREDENTIALS", req);
    }

    /**
     * MUST be declared before {@link #handleDisabled} — Spring's
     * exception-handler resolver picks the most specific match, but
     * explicit ordering keeps the intent obvious to readers. Returns
     * a distinct error code so the frontend can route the user to
     * the registration paywall instead of a generic "account
     * disabled" banner. The {@code paymentId} is surfaced on the body
     * (under {@code message}) so the frontend can resume the right
     * Payment row.
     */
    @ExceptionHandler(RegistrationPaymentPendingException.class)
    public ResponseEntity<APIErrorResponse> handleRegistrationPaymentPending(
            RegistrationPaymentPendingException ex, HttpServletRequest req) {
        APIErrorResponse body = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Registration payment pending — complete the activation fee to sign in.")
                .errorCode("REGISTRATION_PAYMENT_PENDING")
                .path(req.getRequestURI())
                // Stuffed into fieldErrors because the existing
                // APIErrorResponse shape doesn't carry a dedicated
                // "extras" bag — the frontend reads
                // fieldErrors[0].authUserId to resume the paywall.
                .fieldErrors(List.of(Map.of(
                        "authUserId", ex.getPaymentPendingForUserId())))
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<APIErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Account is disabled", "ACCOUNT_DISABLED", req);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<APIErrorResponse> handleLocked(LockedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Account is locked", "ACCOUNT_LOCKED", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<APIErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), "AUTHENTICATION_FAILED", req);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<APIErrorResponse> handleJwt(JwtException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired JWT: " + ex.getMessage(), "INVALID_JWT", req);
    }

    /* ---------- Validation ---------- */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldEntry).toList();
        APIErrorResponse body = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Request body validation failed")
                .errorCode("VALIDATION_FAILED")
                .path(req.getRequestURI())
                .fieldErrors(fields)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<APIErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "CONSTRAINT_VIOLATION", req);
    }

    /* ---------- Request shape ---------- */

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON payload: " + rootCauseMessage(ex), "MALFORMED_JSON", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<APIErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), "MISSING_PARAMETER", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected";
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' must be of type " + required, "PARAMETER_TYPE_MISMATCH", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), "METHOD_NOT_ALLOWED", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No endpoint matches " + req.getMethod() + " " + req.getRequestURI(),
                "ENDPOINT_NOT_FOUND", req);
    }

    /* ---------- Persistence & I/O ---------- */

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), rootCauseMessage(ex));
        return build(HttpStatus.CONFLICT, "Database constraint violated: " + rootCauseMessage(ex),
                "DATA_INTEGRITY_VIOLATION", req);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<APIErrorResponse> handleFeign(FeignException ex, HttpServletRequest req) {
        log.warn("Downstream service error at {}: status={} body={}", req.getRequestURI(), ex.status(), ex.contentUTF8());
        HttpStatus status = HttpStatus.resolve(ex.status() > 0 ? ex.status() : 502);
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        return build(status, "Downstream service call failed: " + ex.getMessage(), "DOWNSTREAM_ERROR", req);
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<APIErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "ILLEGAL_ARGUMENT", req);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<APIErrorResponse> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Requested resource was not found", "NOT_FOUND", req);
    }

    /* ---------- Catch-all ---------- */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.",
                "INTERNAL_ERROR", req);
    }

    /* ---------- Helpers ---------- */

    private ResponseEntity<APIErrorResponse> build(HttpStatus status, String message, String code, HttpServletRequest req) {
        APIErrorResponse body = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .errorCode(code)
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, String> toFieldEntry(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                "rejectedValue", String.valueOf(fe.getRejectedValue()));
    }

    private String rootCauseMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r.getMessage() != null ? r.getMessage() : r.getClass().getSimpleName();
    }
}
