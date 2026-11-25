package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreditLimitCreateRequest(
    @NotNull @PositiveOrZero @Digits(integer = 18, fraction = 2) BigDecimal limitAmount,
    @NotBlank String reason) {}
