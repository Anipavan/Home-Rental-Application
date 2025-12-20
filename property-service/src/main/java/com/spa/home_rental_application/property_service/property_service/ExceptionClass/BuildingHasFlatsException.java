package com.spa.home_rental_application.property_service.property_service.ExceptionClass;

import lombok.Getter;

public class BuildingHasFlatsException extends RuntimeException {
  @Getter
  private final String errorCode;

  public BuildingHasFlatsException(String message){
    super(message);
    this.errorCode = "BUILDING_HAS_FLAT";
  }
    public BuildingHasFlatsException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

}
