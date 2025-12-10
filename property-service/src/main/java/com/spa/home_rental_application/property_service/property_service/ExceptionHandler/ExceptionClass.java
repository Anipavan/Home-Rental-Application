package com.spa.home_rental_application.property_service.property_service.ExceptionHandler;

import com.spa.home_rental_application.property_service.property_service.ExceptionClass.DTO.APIErrorResponse;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class ExceptionClass {
    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<APIErrorResponse> handleRecordNotFoundException(RecordNotFoundException ex){

        APIErrorResponse error = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();
        return  new ResponseEntity<>(error,HttpStatus.NOT_FOUND);
    }
}
