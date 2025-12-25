package com.spa.home_rental_application.user_service.user_service.ExceptionHandler;

import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO.APIErrorResponse;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO.ErrorResponse;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO.FieldErrorDto;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class HandleException {

    @ExceptionHandler(RecordNotFound.class)
    public ResponseEntity<APIErrorResponse> handleRecordNotFoundException(RecordNotFound ex){
        APIErrorResponse error = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();
        return  new ResponseEntity<>(error, HttpStatus.NOT_FOUND);

    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException  ex){
        List<FieldErrorDto> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new FieldErrorDto(err.getField(), err.getDefaultMessage()))
                .toList();
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR",
                "Request has invalid fields",
                errors);
        return ResponseEntity.badRequest().body(response);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(DataIntegrityViolationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Data integrity violation");
        body.put("message", "Duplicate or invalid data (unique/email/GST/PAN, etc.)");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
