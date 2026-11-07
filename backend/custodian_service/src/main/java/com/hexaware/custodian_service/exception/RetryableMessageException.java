package com.hexaware.custodian_service.exception;

/**
 * Handlers may use this for a known transient dependency failure; do not include payloads or
 * secrets.
 */
public class RetryableMessageException extends RuntimeException {
  public RetryableMessageException() {
    super("TRANSIENT_HANDLER_FAILURE");
  }
}
