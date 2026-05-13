package com.spa.home_rental_application.property_service.property_service.ExceptionClass;

import lombok.Getter;

/**
 * Thrown when a tenant tries to schedule a vacate but still has
 * unpaid rent invoices on the flat. The product spec requires every
 * outstanding due (PENDING or OVERDUE) to be cleared first.
 *
 * <p>Mapped to HTTP 422 (Unprocessable Entity) by the global
 * exception handler — semantically the request was well-formed but
 * blocked by a business rule.
 */
public class OutstandingDuesException extends RuntimeException {
  @Getter
  private final String errorCode;

  public OutstandingDuesException(String message) {
    super(message);
    this.errorCode = "OUTSTANDING_DUES";
  }

  public OutstandingDuesException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }
}
