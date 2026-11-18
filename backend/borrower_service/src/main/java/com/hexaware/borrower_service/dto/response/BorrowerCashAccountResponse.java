package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.BorrowerCashAccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerCashAccountResponse(
    Long accountId,
    String borrowerOrganizationReference,
    String currency,
    BigDecimal cashBalance,
    BorrowerCashAccountStatus status,
    Instant createdAt,
    Instant updatedAt) {}
