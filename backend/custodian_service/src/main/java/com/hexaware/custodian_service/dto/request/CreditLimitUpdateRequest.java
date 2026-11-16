package com.hexaware.custodian_service.dto.request;

import com.hexaware.custodian_service.enums.RuleStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreditLimitUpdateRequest(
    @NotNull @PositiveOrZero @Digits(integer = 18, fraction = 2) BigDecimal limitAmount,
    @NotNull RuleStatus status,
    @NotNull Long version,
    @NotBlank String reason) {}
