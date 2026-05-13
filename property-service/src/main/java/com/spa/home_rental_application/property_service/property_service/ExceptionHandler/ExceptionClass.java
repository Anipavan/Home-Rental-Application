package com.spa.home_rental_application.property_service.property_service.ExceptionHandler;

import com.spa.home_rental_application.property_service.property_service.ExceptionClass.BuildingHasFlatsException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.DTO.APIErrorResponse;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.FlatOccupiedException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.InvalidLeasePeriodException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Global exception translator. Produces a consistent {@link APIErrorResponse}
 * envelope for every error path so clients never see raw stack traces or
 * mismatched HTTP status semantics.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Domain exceptions (RecordNotFound, BuildingHasFlats, FlatOccupied,
 *       InvalidLeasePeriod)</li>
 *   <li>Bean Validation — {@link MethodArgumentNotValidException} on bodies,
 *       {@link ConstraintViolationException} on path/query params</li>
 *   <li>Request shape — malformed JSON, missing params, type mismatches,
 *       wrong HTTP method, unknown route</li>
 *   <li>Persistence — {@link DataIntegrityViolationException}</li>
 *   <li>Uploads — {@link MaxUploadSizeExceededException}, generic multipart</li>
 *   <li>I/O failures — {@link IOException}</li>
 *   <li>Argument validation in service code — {@link IllegalArgumentException}</li>
 *   <li>Catch-all for unexpected {@link Exception}s</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class ExceptionClass {

    // ---------- Domain exceptions ----------

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<APIErrorResponse> handleRecordNotFound(RecordNotFoundException ex, HttpServletRequest req) {
        log.warn("Record not found at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(BuildingHasFlatsException.class)
    public ResponseEntity<APIErrorResponse> handleBuildingHasFlats(BuildingHasFlatsException ex, HttpServletRequest req) {
        log.warn("Building delete blocked at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(FlatOccupiedException.class)
    public ResponseEntity<APIErrorResponse> handleFlatOccupied(FlatOccupiedException ex, HttpServletRequest req) {
        log.warn("Flat already occupied at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(InvalidLeasePeriodException.class)
    public ResponseEntity<APIErrorResponse> handleInvalidLeasePeriod(InvalidLeasePeriodException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrorCode(), req);
    }

    @ExceptionHandler(com.spa.home_rental_application.property_service.property_service.ExceptionClass.OutstandingDuesException.class)
    public ResponseEntity<APIErrorResponse> handleOutstandingDues(
            com.spa.home_rental_application.property_service.property_service.ExceptionClass.OutstandingDuesException ex,
            HttpServletRequest req) {
        log.warn("Vacate blocked by outstanding dues at {}: {}", req.getRequestURI(), ex.getMessage());
        // 422 Unprocessable Entity — well-formed but business-rule blocked
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getErrorCode(), req);
    }

    // ---------- Bean validation ----------

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

    // ---------- Request shape ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Malformed JSON payload: " + rootCauseMessage(ex),
                "MALFORMED_JSON", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<APIErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(),
                "MISSING_PARAMETER", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected";
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' must be of type " + required,
                "PARAMETER_TYPE_MISMATCH", req);
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

    // ---------- Persistence ----------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), rootCauseMessage(ex));
        return build(HttpStatus.CONFLICT,
                "Database constraint violated: " + rootCauseMessage(ex),
                "DATA_INTEGRITY_VIOLATION", req);
    }

    // ---------- Uploads ----------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<APIErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the configured size limit",
                "FILE_TOO_LARGE", req);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<APIErrorResponse> handleMultipart(MultipartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Multipart request invalid: " + rootCauseMessage(ex),
                "MULTIPART_ERROR", req);
    }

    // ---------- I/O & argument validation ----------

    @ExceptionHandler(IOException.class)
    public ResponseEntity<APIErrorResponse> handleIo(IOException ex, HttpServletRequest req) {
        log.error("I/O failure at {}: {}", req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An I/O error occurred while processing the request",
                "IO_ERROR", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<APIErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "ILLEGAL_ARGUMENT", req);
    }

    // ---------- Catch-all ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.",
                "INTERNAL_ERROR", req);
    }

    // ---------- Helpers ----------

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
