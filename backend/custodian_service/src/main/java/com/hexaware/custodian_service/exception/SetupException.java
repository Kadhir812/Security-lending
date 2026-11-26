package com.hexaware.custodian_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SetupException extends RuntimeException {
  private final HttpStatus status;

  public SetupException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static SetupException invalid(String message) {
    return new SetupException(HttpStatus.BAD_REQUEST, message);
  }

  public static SetupException missing(String message) {
    return new SetupException(HttpStatus.NOT_FOUND, message);
  }

  public static SetupException conflict(String message) {
    return new SetupException(HttpStatus.CONFLICT, message);
  }

  public static SetupException unavailable(String message) {
    return new SetupException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
