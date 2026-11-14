package com.hexaware.custodian_service.dto.request;

import com.hexaware.custodian_service.enums.CollateralType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CollateralHaircutRequest(
    @NotNull CollateralType collateralType,
    @NotNull String currency,
    @NotNull @Digits(integer = 5, fraction = 4) BigDecimal haircutPercentage,
    @NotNull Instant effectiveFrom,
    Instant effectiveTo,
    Long version) {}
