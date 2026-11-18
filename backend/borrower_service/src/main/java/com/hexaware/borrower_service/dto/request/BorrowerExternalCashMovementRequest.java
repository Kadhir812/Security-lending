package com.hexaware.borrower_service.dto.request;

import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerExternalCashMovementRequest(
    @NotNull BorrowerCashTransactionType transactionType,
    @NotNull @Positive @Digits(integer = 18, fraction = 2) BigDecimal amount,
    @NotBlank @Size(max = 80) String externalReference,
    @NotBlank @Size(max = 255) String description,
    @NotNull @PastOrPresent Instant transactionTime) {}
