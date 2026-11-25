package com.hexaware.borrower_service.enums;

public enum OutboxEventStatus {
  PENDING,
  PUBLISHING,
  PUBLISHED,
  FAILED
}
