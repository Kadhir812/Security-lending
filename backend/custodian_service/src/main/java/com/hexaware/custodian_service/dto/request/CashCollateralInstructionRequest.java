package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashCollateralInstructionRequest(
    @NotBlank String instructionReference,
    @NotBlank String currency,
    @NotNull @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 18, fraction = 2)
        BigDecimal amount,
    @NotBlank String fromAccount,
    @NotBlank String toAccount,
    @NotBlank String collateralType) {}
