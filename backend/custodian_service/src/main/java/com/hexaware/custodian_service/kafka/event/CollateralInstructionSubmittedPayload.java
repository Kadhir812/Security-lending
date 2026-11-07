package com.hexaware.custodian_service.kafka.event;

import java.math.BigDecimal;

public record CollateralInstructionSubmittedPayload(
    Integer payloadVersion,
    String transactionReference,
    String instructionReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference,
    BigDecimal amount,
    String currency,
    String collateralType,
    String marginCallReference) {}
