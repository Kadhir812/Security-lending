package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.enums.RuleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LendingFeeResponse(
    Long feeId,
    String mslaReference,
    String securityIsin,
    BigDecimal annualFeePct,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    RuleStatus status,
    Long version) {}
