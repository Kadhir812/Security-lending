package com.hexaware.custodian_service.enums;

public enum OutboxEventStatus {
  PENDING,
  PUBLISHING,
  PUBLISHED,
  FAILED
}
