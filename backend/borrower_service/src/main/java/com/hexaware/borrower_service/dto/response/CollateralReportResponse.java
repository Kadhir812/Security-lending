package com.hexaware.borrower_service.dto.response;

import java.time.Instant;

public record CollateralReportResponse(
    String reportId,
    String inquiryId,
    String requestReference,
    String mslaReference,
    String status,
    String collateralType,
    String currency,
    String haircutPercentage,
    String coveragePercentage,
    Instant receivedAt) {}
