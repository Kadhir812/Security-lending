package com.hexaware.custodian_service.dto.request;

import com.hexaware.custodian_service.enums.RuleStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LendingFeeRequest(
    String securityIsin,
    @NotNull @PositiveOrZero @Digits(integer = 4, fraction = 6) BigDecimal annualFeePct,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotNull RuleStatus status,
    Long version) {}
