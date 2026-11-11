package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import java.time.Instant;
import java.time.LocalDate;

public record LoanRequestResponse(
    String requestReference,
    String mslaReference,
    String locateReference,
    String securityIsin,
    Long quantity,
    LocalDate requestedSettlementDate,
    String settlementCurrency,
    SubmissionStatus submissionStatus,
    LoanRequestStatus status,
    String responseReason,
    Instant respondedAt,
    Instant requestedAt,
    Long version) {}
