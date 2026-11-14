package com.hexaware.borrower_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BorrowerException extends RuntimeException {
  private final HttpStatus status;

  public BorrowerException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static BorrowerException invalid(String message) {
    return new BorrowerException(HttpStatus.BAD_REQUEST, message);
  }

  public static BorrowerException missing(String message) {
    return new BorrowerException(HttpStatus.NOT_FOUND, message);
  }

  public static BorrowerException conflict(String message) {
    return new BorrowerException(HttpStatus.CONFLICT, message);
  }

  public static BorrowerException unavailable(String message) {
    return new BorrowerException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
