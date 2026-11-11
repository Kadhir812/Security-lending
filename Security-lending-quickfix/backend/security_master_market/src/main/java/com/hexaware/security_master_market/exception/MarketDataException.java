package com.hexaware.security_master_market.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MarketDataException extends RuntimeException {
  private final HttpStatus status;

  public MarketDataException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static MarketDataException invalid(String message) {
    return new MarketDataException(HttpStatus.BAD_REQUEST, message);
  }

  public static MarketDataException missing(String message) {
    return new MarketDataException(HttpStatus.NOT_FOUND, message);
  }

  public static MarketDataException conflict(String message) {
    return new MarketDataException(HttpStatus.CONFLICT, message);
  }
}
