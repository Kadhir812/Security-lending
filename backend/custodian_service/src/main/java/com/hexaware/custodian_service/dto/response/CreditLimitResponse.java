package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.enums.RuleStatus;
import java.math.BigDecimal;

public record CreditLimitResponse(
    String mslaReference,
    String currency,
    BigDecimal limitAmount,
    BigDecimal usedAmount,
    BigDecimal reservedAmount,
    BigDecimal availableAmount,
    RuleStatus status,
    Long version) {}
