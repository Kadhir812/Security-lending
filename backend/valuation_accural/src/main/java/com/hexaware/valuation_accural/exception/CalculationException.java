package com.hexaware.valuation_accural.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CalculationException extends RuntimeException {
  private final HttpStatus status;

  public CalculationException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static CalculationException unavailable(String message) {
    return new CalculationException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
