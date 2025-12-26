package com.spa.home_rental_application.property_service.property_service.ExceptionClass;

import lombok.Getter;

public class FlatOccupiedException extends RuntimeException {
  @Getter
  private final String errorCode;

  public FlatOccupiedException(String message) {
    super(message);
    this.errorCode = "FLAT_OCCUPIED";
  }
    public FlatOccupiedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
