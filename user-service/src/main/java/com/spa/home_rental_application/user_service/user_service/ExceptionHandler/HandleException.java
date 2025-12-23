package com.spa.home_rental_application.user_service.user_service.ExceptionHandler;

import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DTO.APIErrorResponse;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class HandleException {

    @ExceptionHandler(RecordNotFound.class)
    public ResponseEntity<APIErrorResponse> handleRecordNotFoundException(RecordNotFound ex){
        APIErrorResponse error = APIErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();
        return  new ResponseEntity<>(error, HttpStatus.IM_USED);

    }

}
