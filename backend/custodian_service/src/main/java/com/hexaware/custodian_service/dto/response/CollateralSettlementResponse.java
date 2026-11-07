package com.hexaware.custodian_service.dto.response;

public record CollateralSettlementResponse(
    String instructionReference,
    String contractStatus,
    String cashStatus,
    String currency,
    String settlementStatus,
    java.math.BigDecimal confirmedAmount,
    boolean simulatedConfirmation) {}
