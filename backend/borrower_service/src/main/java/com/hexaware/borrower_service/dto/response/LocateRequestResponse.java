package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import java.time.Instant;

public record LocateRequestResponse(
    String locateReference,
    String mslaReference,
    String securityIsin,
    Long quantity,
    Long approvedQuantity,
    LocateStatus status,
    String responseReason,
    SubmissionStatus submissionStatus,
    Instant requestTime,
    Instant respondedAt,
    Instant expiryTime) {}
