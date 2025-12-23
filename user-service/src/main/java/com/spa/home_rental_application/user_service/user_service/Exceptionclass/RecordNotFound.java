package com.spa.home_rental_application.user_service.user_service.Exceptionclass;

import lombok.Getter;

@Getter
public class RecordNotFound extends RuntimeException{
    private  String errorCode;

    public RecordNotFound(String message){
        super(message);
        this.errorCode="RECORD_NOT_FOUND";
    }

    public RecordNotFound(String message,String errorcode){
        super(message);
        this.errorCode=errorcode;
    }

}

