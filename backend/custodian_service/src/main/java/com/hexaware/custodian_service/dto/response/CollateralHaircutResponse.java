package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.enums.CollateralType;
import java.math.BigDecimal;
import java.time.Instant;

public record CollateralHaircutResponse(
    Long haircutId,
    String mslaReference,
    CollateralType collateralType,
    String currency,
    BigDecimal haircutPercentage,
    Instant effectiveFrom,
    Instant effectiveTo,
    Long version) {}
