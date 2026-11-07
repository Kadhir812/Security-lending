package com.hexaware.custodian_service.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditLimitHistoryResponse(
    BigDecimal oldLimit,
    BigDecimal newLimit,
    String reason,
    Long changedBy,
    Instant changedAt) {}
