package com.hexaware.borrower_service.dto.response;

public record BorrowerHoldingPosition(
    String securityIsin,
    String symbol,
    String securityName,
    String securityType,
    String currency,
    long ownedQuantity,
    long borrowedOutstandingQuantity,
    long openShortQuantity,
    long borrowedNotShortedQuantity) {}
