package com.hexaware.custodian_service.kafka.event;

import java.time.LocalDate;

public record LoanRequestSubmittedPayload(
    Integer payloadVersion,
    String requestReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference,
    String mslaReference,
    String locateReference,
    String securityIsin,
    Long quantity,
    LocalDate requestedSettlementDate,
    String settlementCurrency,
    String submittedByIssuer,
    String submittedBySubject) {}
