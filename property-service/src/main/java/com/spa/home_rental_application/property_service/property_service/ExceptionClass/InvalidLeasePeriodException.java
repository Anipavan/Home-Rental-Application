package com.spa.home_rental_application.property_service.property_service.ExceptionClass;

import lombok.Getter;

public class InvalidLeasePeriodException extends RuntimeException {
  @Getter
  private final String errorCode;
    public InvalidLeasePeriodException(String message) {
        super(message);
        this.errorCode = "INVALID_LEASE_PERIOD";
    }

  public InvalidLeasePeriodException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }
}
