package com.hexaware.custodian_service.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MarginCallResponse(
    String marginCallReference,
    String contractReference,
    String valuationResultReference,
    BigDecimal requiredCollateral,
    BigDecimal currentCollateral,
    BigDecimal shortfallAmount,
    BigDecimal outstandingAmount,
    String status,
    Instant createdAt,
    Instant dueAt,
    Instant resolvedAt,
    Map<String, Object> calculationSnapshot) {}
