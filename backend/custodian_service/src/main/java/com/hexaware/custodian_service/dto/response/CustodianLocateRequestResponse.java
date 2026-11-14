package com.hexaware.custodian_service.dto.response;

import java.time.Instant;

public record CustodianLocateRequestResponse(
    String locateReference,
    String borrowerOrganizationReference,
    String lenderOrganizationReference,
    String mslaReference,
    String securityIsin,
    Long quantity,
    Long availableQuantity,
    Instant requestTime,
    String status) {}
