package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CashCollateralInstructionRequest(
    @NotBlank String instructionReference,
    @Positive BigDecimal amount,
    @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotBlank String collateralType) {}
