package com.hexaware.valuation_accural.exception;

import lombok.Getter;

@Getter
public class InvalidEnvelopeException extends RuntimeException {
  private final String reason;

  public InvalidEnvelopeException(String reason) {
    super(reason);
    this.reason = reason;
  }
}
