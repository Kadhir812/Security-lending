package com.hexaware.borrower_service.dto.response;

import java.time.Instant;
import java.util.Map;

public record LoanContractViewResponse(
    String requestReference,
    String transactionReference,
    Map<String, Object> contractSnapshot,
    Instant receivedAt) {}
