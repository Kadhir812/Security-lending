package com.hexaware.custodian_service.dto.response;

import java.time.Instant;

public record LocateResponse(
    String locateReference,
    String mslaReference,
    String borrowerOrganizationReference,
    String securityIsin,
    Long quantity,
    Long approvedQuantity,
    String status,
    Instant requestTime,
    Instant expiryTime,
    Instant respondedAt,
    String responseReason) {}
