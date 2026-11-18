package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerCashTransactionResponse(
    String transactionReference,
    String currency,
    BorrowerCashTransactionType transactionType,
    String activityType,
    BigDecimal amount,
    String relatedReferenceType,
    String relatedReference,
    String description,
    Instant transactionTime,
    Instant recordedAt) {}
